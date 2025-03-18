// Copyright (c) Microsoft. All rights reserved.
package dev.langchain4j.openapi;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.service.tool.ToolExecutor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.Duration.ofSeconds;

public class OpenAPIToolsImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            OpenAPIToolsImporter.class);

    private OpenAPIToolsImporter(){}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String toolName;
        private String specPath;
        private Map<String, List<String>>  httpHeaders;
        private HttpClient client;
        private Integer connectionTimeout;
        private Integer readTimeout;

        private String serverUrl;

        public Builder withToolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder withSpecPath(String specPath) {
            this.specPath = specPath;
            return this;
        }

        public Builder withHttpHeaders(Map<String, List<String>>   httpHeaders) {
            this.httpHeaders = httpHeaders;
            return this;
        }

        public Builder withClient(HttpClient client) {
            this.client = client;
            return this;
        }

        public Builder withConnectionTimeout(Integer connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder withReadTimeout(Integer readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder withServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Map<ToolSpecification, ToolExecutor> build() {
            return OpenAPIToolsImporter.fromSchema(toolName, specPath, httpHeaders, client, connectionTimeout,readTimeout, serverUrl);
        }
    }

    public static Map<ToolSpecification, ToolExecutor> fromSchema(
        String pluginName,
        String specPath,
        @Nullable Map<String, List<String>> httpHeaders,
        @Nullable HttpClient client,
        @Nullable Integer connectionTimeout,
        @Nullable Integer readTimeout,
        String serverUrl) {

        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);

        OpenAPI openAPI = new OpenAPIV3Parser().readLocation(specPath, null, parseOptions).getOpenAPI();

        client = getHttpClient(client, connectionTimeout, readTimeout);


       return getSpecifications(
            client,
            pluginName,
            openAPI.getPaths(),
            serverUrl,
            httpHeaders);

    }

    private static HttpClient getHttpClient(
        @Nullable HttpClient client,
        @Nullable Integer connectionTimeout,
        @Nullable Integer readTimeout) {


        //TODO - Http connection pooling

        if( client == null){
            LOGGER.debug("Creating langchain4j default HttpClient");
            HttpClientBuilder httpClientBuilder  = HttpClientBuilderLoader.loadHttpClientBuilder();
            client = httpClientBuilder
                    .connectTimeout( connectionTimeout == null ? ofSeconds(15) : ofSeconds(connectionTimeout))
                    .readTimeout( readTimeout == null ? ofSeconds(60) : ofSeconds(readTimeout))
                    .build();
        }

        return client;
    }

    private static  Map<ToolSpecification, ToolExecutor> getSpecifications(
        HttpClient client,
        String pluginName,
        Paths paths,
        String serverUrl,
        Map<String, List<String>> headers) {

        HashMap<ToolSpecification, ToolExecutor> specifications = new HashMap<>();

        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();

            specifications.putAll(buildSpecificationEntry(
                client,
                pluginName,
                serverUrl,
                path,
                pathItem,
                headers));


        }

        return specifications;

    }

    private static Map<ToolSpecification,ToolExecutor> buildSpecificationEntry(
        HttpClient client,
        String pluginName,
        String serverUrl,
        String path,
        PathItem pathItem,
        Map<String, List<String>> headers) {


        ToolSpecification toolSpecification = null;
        RestClientToolExecutor toolExecutor = null;


        if (pathItem.getGet() != null) {
            checkOperationId(path,"get",pathItem.getGet());
            toolSpecification = getToolSpecificationFromRequest(pluginName,pathItem.getGet());
            toolExecutor = new RestClientToolExecutor(HttpMethod.GET,serverUrl,path,pathItem,null,headers,pathItem.getGet());
        }
        if (pathItem.getPost() != null) {
            checkOperationId(path,"post",pathItem.getPost());
            toolSpecification = getToolSpecificationFromRequest(pluginName,pathItem.getPost());
            toolExecutor = new RestClientToolExecutor(HttpMethod.POST,serverUrl,path,pathItem,null,headers,pathItem.getPost());
        }
        if (pathItem.getDelete() != null) {
            checkOperationId(path,"delete",pathItem.getDelete());
            toolSpecification = getToolSpecificationFromRequest(pluginName,pathItem.getPost());
            toolExecutor = new RestClientToolExecutor(HttpMethod.DELETE,serverUrl,path,pathItem,null,headers,pathItem.getPost());
        }
        if (pathItem.getPut() != null) {
            // toolSpecification = getToolSpecificationFromRequest(pluginName,pathItem.getPost());
            // toolExecutor = new RestClientToolExecutor(HttpMethod.PUT,serverUrl,path,pathItem,null,headers,pathItem.getPost());

            throw new IllegalArgumentException ("PUT method not supported by langchain4j HTTP client");
        }
        if (pathItem.getPatch() != null) {
           // toolSpecification = getToolSpecificationFromRequest(pluginName,pathItem.getPost());
           // toolExecutor = new RestClientToolExecutor(HttpMethod.PUT,serverUrl,path,pathItem,null,headers,pathItem.getPost());
            throw new IllegalArgumentException ("PATCH method not supported by langchain4j HTTP client");
        }

        return  Map.of(toolSpecification, toolExecutor);


    }

    private static void checkOperationId(String path, String get, Operation operation) {
        if(operation.getOperationId() == null){
            throw new IllegalArgumentException("OperationId for path["+path+"] is not defined");
        }
    }


    private static ToolSpecification getToolSpecificationFromRequest(
        String pluginName,
        Operation operation
       ) {

        List<Parameter> parameters = operation.getParameters();
        Map<String, JsonSchemaElement> properties = new HashMap<>();
        List<String> requiredList = new ArrayList<>();

       //create function params for path and query params available in the operation spec.
        if (parameters != null && !parameters.isEmpty()) {
            parameters.forEach(parameter -> {
                JsonSchemaElement jsonSchemaElement = getJsonSchemaElement(parameter);
                properties.put(parameter.getName(),jsonSchemaElement);
                if(parameter.getRequired()) {
                    requiredList.add(parameter.getName());
                }
            });
        }

        //create requestBody params if present. It's serialized as json in a string with description
        RequestBody requestBody = operation.getRequestBody();

        if (requestBody != null) {
            if (requestBody.getContent().get("application/json") != null) {
                MediaType mediaType = requestBody.getContent().get("application/json");
                Schema<?> schema = mediaType.getSchema();

                //properties.put("requestBody",JsonStringSchema.builder().description("Example request body:\n" + RequestBodyUtil.renderJsonExample(schema)).build());
                properties.put("requestBody",RequestBodyUtil.buildRequestBodySchema(schema));
            }

        }


    return ToolSpecification.builder()
                .name(pluginName+"-"+operation.getOperationId())
                .description(operation.getDescription())
                .parameters(JsonObjectSchema.builder().addProperties(properties).required(requiredList).build())
                .build();

    }




    private static JsonSchemaElement getJsonSchemaElement(Parameter parameter) {

        String t = parameter.getSchema().getType();
        JsonSchemaElement jsonSchemaElement = null;
        if (t != null) {
            switch (t) {
                case "integer":
                    jsonSchemaElement = JsonIntegerSchema.builder().description(parameter.getDescription()).build();
                    break;
                case "string":
                    jsonSchemaElement = JsonStringSchema.builder().description(parameter.getDescription()).build();
                    break;
                case "number":
                    jsonSchemaElement = JsonNumberSchema.builder().description(parameter.getDescription()).build();
                    break;
                case "boolean":
                    jsonSchemaElement = JsonBooleanSchema.builder().description(parameter.getDescription()).build();
                    break;
                case "array":
                    jsonSchemaElement = JsonArraySchema.builder().description(parameter.getDescription()).build();
                    break;

                // Not sure if we can support this
                case "null":
                    break;
                case "object":
                    jsonSchemaElement = JsonObjectSchema.builder().description(parameter.getDescription()).build();
                default:
                    break;
            }
        }
        return jsonSchemaElement;
    }


}

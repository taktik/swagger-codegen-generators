package io.swagger.codegen.v3.generators.typescript;

import io.swagger.codegen.v3.CodegenModel;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.CodegenParameter;
import io.swagger.codegen.v3.SupportingFile;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeScriptClientCodegen extends AbstractTypeScriptClientCodegen {

    private static Logger LOGGER = LoggerFactory.getLogger(TypeScriptClientCodegen.class);

    private static final SimpleDateFormat SNAPSHOT_SUFFIX_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");

    protected String npmName = null;
    protected String npmVersion = "1.0.0";
    protected String npmRepository = null;

    protected String classPrefix = "swg";
    protected String skipPathPrefix = "";
    public TypeScriptClientCodegen() {
        super();
        this.outputFolder = "generated-code" + File.separator + "typescript";

    }

    public void setClassPrefix(String classPrefix) {
        this.classPrefix = classPrefix;
    }

    public void setSkipPathPrefix(String skipPathPrefix) {
        this.skipPathPrefix = skipPathPrefix;
    }

    @Override
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
        if (schema instanceof MapSchema  && hasSchemaProperties(schema)) {
            codegenModel.additionalPropertiesType = getTypeDeclaration((Schema) schema.getAdditionalProperties());
            addImport(codegenModel, codegenModel.additionalPropertiesType);
        } else if (schema instanceof MapSchema && hasTrueAdditionalProperties(schema)) {
            codegenModel.additionalPropertiesType = getTypeDeclaration(new ObjectSchema());
        }
    }

    @Override
    public String getName() {
        return "typescript";
    }

    @Override
    public String getHelp() {
        return "Generates a TypeScript Angular (2.x or 4.x) client library.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey("classPrefix")) {
            setClassPrefix((String) additionalProperties.get("classPrefix"));
        }

        if (additionalProperties.containsKey("skipPathPrefix")) {
            setSkipPathPrefix((String) additionalProperties.get("skipPathPrefix"));
        }

        if (StringUtils.isBlank(templateDir)) {
            embeddedTemplateDir = templateDir = getTemplateDir();
        }

        modelTemplateFiles.put("model.mustache", ".ts");
        apiTemplateFiles.put("api.mustache", ".ts");

        languageSpecificPrimitives.add("Blob");
        typeMapping.put("file", "Blob");
        apiPackage = "api";
        modelPackage = "model";

        supportingFiles.add(
                new SupportingFile("models.mustache", modelPackage().replace('.', '/'), "models.ts"));
        supportingFiles
                .add(new SupportingFile("ApiClient.mustache", apiPackage().replace('.', '/'), "Api.ts"));
        supportingFiles.add(new SupportingFile("XHR.mustache", apiPackage().replace('.', '/'), "XHR.ts"));
    }

    private String getIndexDirectory() {
        String indexPackage = modelPackage.substring(0, Math.max(0, modelPackage.lastIndexOf('.')));
        return indexPackage.replace('.', File.separatorChar);
    }

    @Override
    public boolean isDataTypeFile(final String dataType) {
        return dataType != null && dataType.equals("Blob");
    }

    @Override
    public String getArgumentsLocation() {
        return null;
    }

    @Override
    public String getDefaultTemplateDir() {
        return "typescript";
    }

    @Override
    public String getTypeDeclaration(Schema propertySchema) {
        Schema inner;
        if(propertySchema instanceof ArraySchema) {
            ArraySchema arraySchema = (ArraySchema)propertySchema;
            inner = arraySchema.getItems();
            return this.getSchemaType(propertySchema) + "<" + this.getTypeDeclaration(inner) + ">";
        } else if(propertySchema instanceof MapSchema   && hasSchemaProperties(propertySchema)) {
            inner = (Schema) propertySchema.getAdditionalProperties();
            return "{ [key: string]: " + this.getTypeDeclaration(inner) + "; }";
        } else if (propertySchema instanceof MapSchema && hasTrueAdditionalProperties(propertySchema)) {
            inner = new ObjectSchema();
            return "{ [key: string]: " + this.getTypeDeclaration(inner) + "; }";
        } else if(propertySchema instanceof FileSchema || propertySchema instanceof BinarySchema) {
            return "Blob";
        } else if(propertySchema instanceof ObjectSchema) {
            return "any";
        } else {
            return super.getTypeDeclaration(propertySchema);
        }
    }

    @Override
    public String getSchemaType(Schema schema) {
        String swaggerType = super.getSchemaType(schema);
        if(isLanguagePrimitive(swaggerType) || isLanguageGenericType(swaggerType)) {
            return swaggerType;
        }
        applyLocalTypeMapping(swaggerType);
        return swaggerType;
    }

    private String applyLocalTypeMapping(String type) {
        if (typeMapping.containsKey(type)) {
            type = typeMapping.get(type);
        }
        return type;
    }

    private boolean isLanguagePrimitive(String type) {
        return languageSpecificPrimitives.contains(type);
    }

    private boolean isLanguageGenericType(String type) {
        for (String genericType : languageGenericTypes) {
            if (type.startsWith(genericType + "<")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);
        parameter.dataType = applyLocalTypeMapping(parameter.dataType);
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> operations) {
        Map<String, Object> objs = (Map<String, Object>) operations.get("operations");

        // Add filename information for api imports
        objs.put("apiFilename", getApiFilenameFromClassname(objs.get("classname").toString()));

        List<CodegenOperation> ops = (List<CodegenOperation>) objs.get("operation");
        for (CodegenOperation op : ops) {
            // Prep a string buffer where we're going to set up our new version of the string.
            StringBuilder pathBuffer = new StringBuilder();
            StringBuilder parameterName = new StringBuilder();
            int insideCurly = 0;

            // Iterate through existing string, one character at a time.
            for (int i = 0; i < op.path.length(); i++) {
                switch (op.path.charAt(i)) {
                case '{':
                    // We entered curly braces, so track that.
                    insideCurly++;

                    // Add the more complicated component instead of just the brace.
                    pathBuffer.append("${encodeURIComponent(String(");
                    break;
                case '}':
                    // We exited curly braces, so track that.
                    insideCurly--;

                    // Add the more complicated component instead of just the brace.
                    pathBuffer.append(toVarName(parameterName.toString()));
                    pathBuffer.append("))}");
                    parameterName.setLength(0);
                    break;
                default:
                    if (insideCurly > 0) {
                        parameterName.append(op.path.charAt(i));
                    } else {
                        pathBuffer.append(op.path.charAt(i));
                    }
                    break;
                }
            }

            // Overwrite path to TypeScript template string, after applying everything we just did.
            String s = pathBuffer.toString();
            op.path = skipPathPrefix.length() > 0 && s.startsWith(skipPathPrefix) ? s.substring(skipPathPrefix.length()) : s;
        }

        // Add additional filename information for model imports in the services
        List<Map<String, Object>> imports = (List<Map<String, Object>>) operations.get("imports");
        for (Map<String, Object> im : imports) {
            im.put("filename", im.get("import"));
            im.put("classname", getModelnameFromModelFilename(im.get("filename").toString()));
        }

        return operations;
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        Map<String, Object> result = super.postProcessModels(objs);

        // Add additional filename information for imports
        List<Object> models = (List<Object>) postProcessModelsEnum(result).get("models");
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            CodegenModel cm = (CodegenModel) mo.get("model");
            mo.put("tsImports", toTsImports(cm, cm.imports));
        }

        return result;
    }

    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> processedModels) {
        for (Map.Entry<String, Object> entry : processedModels.entrySet()) {
            final Map<String, Object> inner = (Map<String, Object>) entry.getValue();
            final List<Map<String, Object>> models = (List<Map<String, Object>>) inner.get("models");
            for (Map<String, Object> mo : models) {
                final CodegenModel codegenModel = (CodegenModel) mo.get("model");
                if (codegenModel.getIsAlias() && codegenModel.imports != null && !codegenModel.imports.isEmpty()) {
                    mo.put("tsImports", toTsImports(codegenModel, codegenModel.imports));
                }
            }
        }
        return processedModels;
    }

    private List<Map<String, String>> toTsImports(CodegenModel cm, Set<String> imports) {
        List<Map<String, String>> tsImports = new ArrayList<>();
        for (String im : imports) {
            if (!im.equals(cm.classname)) {
                HashMap<String, String> tsImport = new HashMap<>();
                tsImport.put("classname", im);
                tsImport.put("filename", toModelFilename(im));
                tsImports.add(tsImport);
            }
        }
        return tsImports;
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "Default";
        }
        return this.classPrefix + camelize(name, false).replace("Controller", "") + "Api";
    }

    @Override
    public String toApiFilename(String name) {
        if (name.length() == 0) {
            return "Default";
        }
        return this.classPrefix + camelize(name, false).replace("Controller", "") + "Api";
    }

    @Override
    public String toApiImport(String name) {
        return apiPackage() + "/" + toApiFilename(name);
    }

    @Override
    public String toModelFilename(String name) {
        return camelize(toModelName(name), false);
    }

    @Override
    public String toModelImport(String name) {
        return modelPackage() + "/" + toModelFilename(name);
    }

    public String getNpmName() {
        return npmName;
    }

    public void setNpmName(String npmName) {
        this.npmName = npmName;
    }

    public String getNpmVersion() {
        return npmVersion;
    }

    public void setNpmVersion(String npmVersion) {
        this.npmVersion = npmVersion;
    }

    public String getNpmRepository() {
        return npmRepository;
    }

    public void setNpmRepository(String npmRepository) {
        this.npmRepository = npmRepository;
    }

    private String getApiFilenameFromClassname(String classname) {
        String name = classname.substring(0, classname.length() - "Service".length());
        return toApiFilename(name);
    }

    private String getModelnameFromModelFilename(String filename) {
        String name = filename.substring((modelPackage() + File.separator).length());
        return camelize(name);
    }

}

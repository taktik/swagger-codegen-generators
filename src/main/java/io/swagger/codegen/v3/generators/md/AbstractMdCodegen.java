package io.swagger.codegen.v3.generators.md;


import io.swagger.codegen.v3.CodegenModel;
import io.swagger.codegen.v3.CodegenType;
import io.swagger.codegen.v3.generators.DefaultCodegenConfig;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public abstract class AbstractMdCodegen extends DefaultCodegenConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMdCodegen.class);

    // new attributes
//    protected String name;

    public AbstractMdCodegen() {
        super();

        outputFolder = "generated-code" + File.separator + "md";
        modelTemplateFiles.put("model.mustache", ".md");
        apiTemplateFiles.put("api.mustache", ".md");

        // default HIDE_GENERATION_TIMESTAMP to true
        hideGenerationTimestamp = Boolean.TRUE;

        languageSpecificPrimitives = new HashSet<>(
                Arrays.asList()
        );
        defaultIncludes = new HashSet<>(
                Arrays.asList()
        );

        importMapping = new HashMap<>();
    }

    @Override
    public String getArgumentsLocation() {
        return "";
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getHelp() {
        return "Generates a swift client library.";
    }


    @Override
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {

        final Object additionalProperties = schema.getAdditionalProperties();

        if (schema instanceof MapSchema  && hasSchemaProperties(schema)) {
            codegenModel.additionalPropertiesType = getSchemaType((Schema) additionalProperties);
        } else if (schema instanceof MapSchema && hasTrueAdditionalProperties(schema)) {
            codegenModel.additionalPropertiesType = getSchemaType(new ObjectSchema());
        }
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (StringUtils.isBlank(templateDir)) {
            embeddedTemplateDir = templateDir = getTemplateDir();
        }
    }

    @Override
    protected boolean isReservedWord(String word) {
        return word != null && reservedWords.contains(word); //don't lowercase as super does
    }

    @Override
    public String escapeReservedWord(String name) {
        if (this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name);
        }
        return "_" + name;  // add an underscore to the name
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + "model/" + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + "api/" + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String getTypeDeclaration(Schema propertySchema) {
        return super.getTypeDeclaration(propertySchema);
    }

    public String toModelName(String name) {
        if (StringUtils.endsWithAny(name, "Dto")) {
            name = name.substring(0, name.length() - 3);
        }
        name = name.replaceAll("Dto([A-Z])", "$1");
        // camelize the model name
        // phone_number => PhoneNumber
        return camelize(name);
    }

    public String toModelFilename(String name) {
        return initialCaps(toModelName(name));
    }


    @Override
    public String getSchemaType(Schema schema) {
        String schemaType = super.getSchemaType(schema);
        String type;
        if (typeMapping.containsKey(schemaType)) {
            type = typeMapping.get(schemaType);
            if (languageSpecificPrimitives.contains(type) || defaultIncludes.contains(type)) {
                return type;
            }
        } else {
            type = schemaType;
        }
        return toModelName(type);
    }

    @Override
    public boolean isDataTypeFile(String dataType) {
        return dataType != null && dataType.equals("URL");
    }

    @Override
    public boolean isDataTypeBinary(final String dataType) {
        return dataType != null && dataType.equals("Data");
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultAPI";
        }
        return initialCaps(name) + "Api";
    }

    public String getDefaultTemplateDir() {
        return getName();
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

}

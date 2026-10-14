package com.salesmap.backend.minutes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 테스트용 JSON Schema 검사기. contracts/*.schema.json이 쓰는 기능만 지원한다
 * (type·enum·pattern·minLength·minItems·required·properties·additionalProperties:false·items·$ref·oneOf).
 * 백엔드에 스키마 라이브러리 의존성을 늘리지 않으려고 둔다. contracts에 새 키워드를 쓰면 여기도 늘린다.
 */
public final class ContractSchemaValidator {

    // gradle 테스트는 backend/에서 돈다
    private static final Path CONTRACTS_DIR = Path.of("..", "contracts");

    private final JsonNode root;

    private ContractSchemaValidator(JsonNode root) {
        this.root = root;
    }

    public static ContractSchemaValidator load(String schemaFileName) throws IOException {
        return new ContractSchemaValidator(new ObjectMapper().readTree(CONTRACTS_DIR.resolve(schemaFileName).toFile()));
    }

    public static Path contractsDir() {
        return CONTRACTS_DIR;
    }

    /** @return 어긋난 곳 목록. 비어 있으면 통과 */
    public List<String> validate(JsonNode instance) {
        List<String> errors = new ArrayList<>();
        validate(instance, root, "$", errors);
        return errors;
    }

    private void validate(JsonNode instance, JsonNode schema, String path, List<String> errors) {
        if (schema.has("$ref")) {
            validate(instance, resolveRef(schema.get("$ref").asText()), path, errors);
        }
        if (schema.has("oneOf")) {
            int matched = 0;
            for (JsonNode option : schema.get("oneOf")) {
                List<String> optionErrors = new ArrayList<>();
                validate(instance, option, path, optionErrors);
                if (optionErrors.isEmpty()) {
                    matched++;
                }
            }
            if (matched != 1) {
                errors.add(path + ": oneOf 중 정확히 하나와 맞아야 함 (맞은 수 " + matched + ")");
            }
        }
        if (schema.has("type") && !matchesType(instance, schema.get("type"))) {
            errors.add(path + ": type " + schema.get("type") + " 아님 (" + instance.getNodeType() + ")");
            return;
        }
        if (schema.has("enum") && !contains(schema.get("enum"), instance)) {
            errors.add(path + ": enum " + schema.get("enum") + "에 없는 값 " + instance);
        }
        if (instance.isTextual()) {
            if (schema.has("minLength") && instance.asText().length() < schema.get("minLength").asInt()) {
                errors.add(path + ": minLength " + schema.get("minLength") + " 미만");
            }
            if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(instance.asText()).find()) {
                errors.add(path + ": pattern " + schema.get("pattern") + "과 다름 (" + instance.asText() + ")");
            }
        }
        if (instance.isArray()) {
            if (schema.has("minItems") && instance.size() < schema.get("minItems").asInt()) {
                errors.add(path + ": minItems " + schema.get("minItems") + " 미만");
            }
            if (schema.has("items")) {
                for (int index = 0; index < instance.size(); index++) {
                    validate(instance.get(index), schema.get("items"), path + "[" + index + "]", errors);
                }
            }
        }
        if (instance.isObject()) {
            validateObject(instance, schema, path, errors);
        }
    }

    private void validateObject(JsonNode instance, JsonNode schema, String path, List<String> errors) {
        JsonNode properties = schema.path("properties");
        for (JsonNode required : schema.path("required")) {
            if (!instance.has(required.asText())) {
                errors.add(path + ": 필수 키 " + required.asText() + " 없음");
            }
        }
        Iterator<String> names = instance.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (properties.has(name)) {
                validate(instance.get(name), properties.get(name), path + "." + name, errors);
            } else if (schema.path("additionalProperties").isBoolean() && !schema.get("additionalProperties").asBoolean()) {
                errors.add(path + ": 스키마에 없는 키 " + name);
            }
        }
    }

    private JsonNode resolveRef(String ref) {
        if (!ref.startsWith("#/")) {
            throw new IllegalArgumentException("지원하지 않는 $ref: " + ref);
        }
        JsonNode node = root;
        for (String part : ref.substring(2).split("/")) {
            node = node.get(part);
        }
        return node;
    }

    private boolean matchesType(JsonNode instance, JsonNode type) {
        if (type.isArray()) {
            for (JsonNode option : type) {
                if (matchesType(instance, option)) {
                    return true;
                }
            }
            return false;
        }
        return switch (type.asText()) {
            case "string" -> instance.isTextual();
            case "null" -> instance.isNull();
            case "array" -> instance.isArray();
            case "object" -> instance.isObject();
            case "boolean" -> instance.isBoolean();
            case "integer" -> instance.isIntegralNumber();
            case "number" -> instance.isNumber();
            default -> throw new IllegalArgumentException("지원하지 않는 type: " + type);
        };
    }

    private boolean contains(JsonNode values, JsonNode instance) {
        for (JsonNode value : values) {
            if (value.equals(instance)) {
                return true;
            }
        }
        return false;
    }
}

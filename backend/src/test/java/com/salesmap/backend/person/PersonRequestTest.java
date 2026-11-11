package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PersonRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings={
        "{}", "null", "[]", "{\"name\":\" \",\"isSynthetic\":true}",
        "{\"name\":1,\"isSynthetic\":true}", "{\"name\":\"가상인물\",\"isSynthetic\":\"true\"}",
        "{\"name\":\"가상인물\",\"isSynthetic\":null}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"ownerId\":8}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"personId\":\"p_17\"}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"company\":1}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"emails\":null}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"phones\":[null]}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"channels\":[\"UNKNOWN\"]}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"photoAvailable\":null}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"companyHistory\":[{}]}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"companyHistory\":[{\"company\":\"가상회사\",\"until\":\"2025-02-30\"}]}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"companyHistory\":[{\"company\":\"가상회사\",\"until\":\"2025-1-1\"}]}",
        "{\"name\":\"가상인물\",\"isSynthetic\":true,\"companyHistory\":[{\"company\":\"가상회사\",\"extra\":true}]}"
    })
    void rejectsInvalidShapesInsteadOfGuessingOrCoercing(String body) throws Exception {
        assertThatThrownBy(() -> PersonRequest.parse(mapper.readTree(body))).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test void rejectsOversizedInput() throws Exception {
        var body=mapper.createObjectNode().put("name","가".repeat(64000)).put("isSynthetic",true);
        assertThatThrownBy(() -> PersonRequest.parse(body)).isInstanceOf(IllegalArgumentException.class);
    }
}

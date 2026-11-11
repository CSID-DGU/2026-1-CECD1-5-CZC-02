package com.salesmap.backend.person;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** contracts/person.schema.json에 맞춘 출력. 소유자·저장 메타데이터는 내보내지 않는다. */
public record PersonDocument(
        String personId, String name, List<String> nameVariants,
        String company, String department, String title,
        List<String> emails, List<String> phones, List<String> channels,
        List<CompanyHistory> companyHistory,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean photoAvailable,
        @JsonProperty("isSynthetic") boolean isSynthetic
) {
    public record CompanyHistory(String company, String title, String until) {}

    PersonDocument withId(Long id) {
        return new PersonDocument("p_" + id, name, nameVariants, company, department, title,
                emails, phones, channels, companyHistory, photoAvailable, isSynthetic);
    }
}

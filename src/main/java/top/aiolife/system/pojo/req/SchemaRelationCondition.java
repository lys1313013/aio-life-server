package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchemaRelationCondition(
        @NotBlank @Pattern(regexp = "source|target") String side,
        @NotBlank @Size(max = 63) String column,
        @NotBlank @Pattern(regexp = "eq|isNull|isNotNull") String operator,
        @Size(max = 300) String value
) {}

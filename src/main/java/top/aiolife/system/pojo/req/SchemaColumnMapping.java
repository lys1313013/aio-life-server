package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchemaColumnMapping(
        @NotBlank @Size(max = 63) String sourceColumn,
        @NotBlank @Size(max = 63) String targetColumn
) {}

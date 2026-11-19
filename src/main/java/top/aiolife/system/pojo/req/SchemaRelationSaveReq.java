package top.aiolife.system.pojo.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SchemaRelationSaveReq(
        @NotBlank @Size(max = 63) String sourceTable,
        @NotBlank @Size(max = 63) String targetTable,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,119}") String relationKey,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "reference|owner|polymorphic|hierarchy|dictionary|scoped") String relationKind,
        @NotBlank @Pattern(regexp = "(1|0[.][.]1|N|0[.][.]N):(1|0[.][.]1|N|0[.][.]N)") String cardinality,
        @NotEmpty @Size(max = 16) List<@Valid @NotNull SchemaColumnMapping> columnMapping,
        @NotNull @Size(max = 16) List<@Valid @NotNull SchemaRelationCondition> conditions,
        @Size(max = 800) String description
) {}

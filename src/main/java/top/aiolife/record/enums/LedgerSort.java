package top.aiolife.record.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 收支明细的分页排序方式。 */
public enum LedgerSort {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_ASC,
    AMOUNT_DESC;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LedgerSort fromParameter(String value) {
        return valueOf(value);
    }
}

package top.aiolife.core.pojo.entity;

/**
 * PostgreSQL 持久化实体的统一身份比较，不用于 DTO / VO 的值比较。
 *
 * <p>只有运行时类型相同且非空主键相同的两个实例才相等；未赋主键的实例仅与自身相等。
 * 业务字段、审计字段及逻辑删除状态均不参与比较，主键赋值后不得修改。
 *
 * <p>哈希值按类型固定，避免对象加入哈希集合后因分配主键或更新字段而无法查找。
 * 同类型实体会发生哈希冲突；大量数据的索引和去重应显式使用主键作为 Map / Set 的键。
 */
public abstract class IdentityEntity {

    /** 返回该实体实际映射的主键，不增加持久化字段或 JSON 属性。 */
    protected abstract Object identityKey();

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Object key = identityKey();
        return key != null && key.equals(((IdentityEntity) other).identityKey());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}

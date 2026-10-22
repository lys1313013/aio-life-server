package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.DeviceExpenseEntity;
import top.aiolife.record.pojo.vo.ExpenseDeviceVO;

public interface DeviceExpenseMapper extends BaseMapper<DeviceExpenseEntity> {
    /** expense_id 包含已解绑记录也唯一；重新绑定复用原记录，保留最初创建审计。 */
    @Insert("""
        INSERT INTO device_expense
          (id, user_id, device_id, expense_id, is_deleted, create_user, create_time, update_user, update_time)
        VALUES (#{id}, #{userId}, #{deviceId}, #{expenseId}, 0, #{userId}, NOW(), #{userId}, NOW())
        ON DUPLICATE KEY UPDATE device_id = VALUES(device_id), is_deleted = 0,
          update_user = VALUES(update_user), update_time = NOW()
        """)
    int bind(DeviceExpenseEntity entity);

    @Select("""
        <script>
        SELECT r.expense_id, r.device_id, d.name AS device_name
        FROM device_expense r
        JOIN device d ON d.id = r.device_id AND d.user_id = r.user_id AND d.is_deleted = 0
        JOIN expense e ON e.id = r.expense_id AND e.user_id = r.user_id AND e.is_deleted = 0
        WHERE r.user_id = #{userId} AND r.is_deleted = 0 AND r.expense_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<ExpenseDeviceVO> findLinks(@Param("userId") long userId, @Param("ids") List<Long> ids);
}

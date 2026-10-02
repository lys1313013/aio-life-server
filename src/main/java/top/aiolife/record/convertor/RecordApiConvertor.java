package top.aiolife.record.convertor;

import java.util.List;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.entity.HonorCategoryEntity;
import top.aiolife.record.pojo.entity.HonorRecordEntity;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.entity.MbtiResultEntity;
import top.aiolife.record.pojo.entity.MemoEntity;
import top.aiolife.record.pojo.entity.MilestoneEntity;
import top.aiolife.record.pojo.entity.PasswordVaultEntity;
import top.aiolife.record.pojo.entity.PerformanceEntity;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.entity.TaskColumnEntity;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.entity.ThoughtRelaEventEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.BVideoQuery;
import top.aiolife.record.pojo.query.DeviceQuery;
import top.aiolife.record.pojo.query.ExerciseRecordQuery;
import top.aiolife.record.pojo.query.MemoQuery;
import top.aiolife.record.pojo.query.SysDictTypeFilterQuery;
import top.aiolife.record.pojo.query.TaskColumnQuery;
import top.aiolife.record.pojo.query.ThoughtQuery;
import top.aiolife.record.pojo.query.TimeRecordQuery;
import top.aiolife.record.pojo.query.UserDictDataQuery;
import top.aiolife.record.pojo.req.AnniversaryRecordCreateReq;
import top.aiolife.record.pojo.req.AnniversaryRecordUpdateReq;
import top.aiolife.record.pojo.req.BVideoCreateReq;
import top.aiolife.record.pojo.req.BVideoProgressReq;
import top.aiolife.record.pojo.req.BVideoUpdateReq;
import top.aiolife.record.pojo.req.DeviceCreateReq;
import top.aiolife.record.pojo.req.DeviceUpdateReq;
import top.aiolife.record.pojo.req.ExerciseRecordCreateReq;
import top.aiolife.record.pojo.req.ExerciseRecordUpdateReq;
import top.aiolife.record.pojo.req.ExpenseCreateReq;
import top.aiolife.record.pojo.req.ExpenseUpdateReq;
import top.aiolife.record.pojo.req.GoalCreateReq;
import top.aiolife.record.pojo.req.GoalUpdateReq;
import top.aiolife.record.pojo.req.HonorRecordCreateReq;
import top.aiolife.record.pojo.req.HonorRecordUpdateReq;
import top.aiolife.record.pojo.req.IncomeCreateReq;
import top.aiolife.record.pojo.req.IncomeUpdateReq;
import top.aiolife.record.pojo.req.MemoCreateReq;
import top.aiolife.record.pojo.req.MemoUpdateReq;
import top.aiolife.record.pojo.req.MilestoneCreateReq;
import top.aiolife.record.pojo.req.MilestoneUpdateReq;
import top.aiolife.record.pojo.req.MovieCreateReq;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.req.PasswordVaultCreateReq;
import top.aiolife.record.pojo.req.PasswordVaultUpdateReq;
import top.aiolife.record.pojo.req.PerformanceCreateReq;
import top.aiolife.record.pojo.req.PerformanceUpdateReq;
import top.aiolife.record.pojo.req.ReadRecordCreateReq;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.pojo.req.SysDictDataCreateReq;
import top.aiolife.record.pojo.req.SysDictDataUpdateReq;
import top.aiolife.record.pojo.req.SysDictTypeCreateReq;
import top.aiolife.record.pojo.req.SysDictTypeUpdateReq;
import top.aiolife.record.pojo.req.TaskColumnCreateReq;
import top.aiolife.record.pojo.req.TaskColumnSortReq;
import top.aiolife.record.pojo.req.TaskColumnUpdateReq;
import top.aiolife.record.pojo.req.TaskCreateReq;
import top.aiolife.record.pojo.req.TaskDetailCreateReq;
import top.aiolife.record.pojo.req.TaskDetailSortReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;
import top.aiolife.record.pojo.req.TaskSortReq;
import top.aiolife.record.pojo.req.TaskUpdateReq;
import top.aiolife.record.pojo.req.ThoughtEventUpdateReq;
import top.aiolife.record.pojo.req.ThoughtUpdateReq;
import top.aiolife.record.pojo.req.TimeRecordDeleteByDateReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.req.TimeRecordSaveReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryAdminCreateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryAdminUpdateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryCreateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategorySortReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryUpdateReq;
import top.aiolife.record.pojo.req.UserBindCreateReq;
import top.aiolife.record.pojo.req.UserBindUpdateReq;
import top.aiolife.record.pojo.req.UserDictDataAdminCreateReq;
import top.aiolife.record.pojo.req.UserDictDataAdminUpdateReq;
import top.aiolife.record.pojo.req.UserDictDataCreateReq;
import top.aiolife.record.pojo.req.UserDictDataUpdateReq;
import top.aiolife.record.pojo.vo.AnniversaryRecordVO;
import top.aiolife.record.pojo.vo.BVideoVO;
import top.aiolife.record.pojo.vo.DeviceVO;
import top.aiolife.record.pojo.vo.ExerciseRecordVO;
import top.aiolife.record.pojo.vo.ExpenseVO;
import top.aiolife.record.pojo.vo.GoalVO;
import top.aiolife.record.pojo.vo.HonorCategoryVO;
import top.aiolife.record.pojo.vo.HonorRecordVO;
import top.aiolife.record.pojo.vo.IncomeVO;
import top.aiolife.record.pojo.vo.MbtiResultVO;
import top.aiolife.record.pojo.vo.MemoVO;
import top.aiolife.record.pojo.vo.MilestoneVO;
import top.aiolife.record.pojo.vo.PasswordVaultVO;
import top.aiolife.record.pojo.vo.PerformanceVO;
import top.aiolife.record.pojo.vo.SysDictDataRecordVO;
import top.aiolife.record.pojo.vo.SysDictTypeVO;
import top.aiolife.record.pojo.vo.TaskColumnVO;
import top.aiolife.record.pojo.vo.TaskDetailVO;
import top.aiolife.record.pojo.vo.TaskVO;
import top.aiolife.record.pojo.vo.ThoughtEventVO;
import top.aiolife.record.pojo.vo.ThoughtRecordVO;
import top.aiolife.record.pojo.vo.TimeRecordListVO;
import top.aiolife.record.pojo.vo.TimeTrackerCategoryVO;
import top.aiolife.record.pojo.vo.UserBindVO;
import top.aiolife.record.pojo.vo.UserDictDataVO;

/** 接口模型与持久化模型的显式转换，响应仅暴露 VO 声明的字段。 */
@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecordApiConvertor {
    RecordApiConvertor INSTANCE = Mappers.getMapper(RecordApiConvertor.class);

    MilestoneVO toMilestoneVO(MilestoneEntity entity);
    List<MilestoneVO> toMilestoneVOList(List<MilestoneEntity> entities);

    default PageResp<MilestoneVO> toMilestoneVOPage(PageResp<MilestoneEntity> page) {
        return page == null ? null : page.map(this::toMilestoneVO);
    }

    DeviceVO toDeviceVO(DeviceEntity entity);
    List<DeviceVO> toDeviceVOList(List<DeviceEntity> entities);

    default PageResp<DeviceVO> toDeviceVOPage(PageResp<DeviceEntity> page) {
        return page == null ? null : page.map(this::toDeviceVO);
    }

    UserDictDataVO toUserDictDataVO(UserDictDataEntity entity);
    List<UserDictDataVO> toUserDictDataVOList(List<UserDictDataEntity> entities);

    default PageResp<UserDictDataVO> toUserDictDataVOPage(PageResp<UserDictDataEntity> page) {
        return page == null ? null : page.map(this::toUserDictDataVO);
    }

    HonorRecordVO toHonorRecordVO(HonorRecordEntity entity);
    List<HonorRecordVO> toHonorRecordVOList(List<HonorRecordEntity> entities);

    default PageResp<HonorRecordVO> toHonorRecordVOPage(PageResp<HonorRecordEntity> page) {
        return page == null ? null : page.map(this::toHonorRecordVO);
    }

    SysDictTypeVO toSysDictTypeVO(SysDictTypeEntity entity);
    List<SysDictTypeVO> toSysDictTypeVOList(List<SysDictTypeEntity> entities);

    default PageResp<SysDictTypeVO> toSysDictTypeVOPage(PageResp<SysDictTypeEntity> page) {
        return page == null ? null : page.map(this::toSysDictTypeVO);
    }

    BVideoVO toBVideoVO(BVideoEntity entity);
    List<BVideoVO> toBVideoVOList(List<BVideoEntity> entities);

    default PageResp<BVideoVO> toBVideoVOPage(PageResp<BVideoEntity> page) {
        return page == null ? null : page.map(this::toBVideoVO);
    }

    ExpenseVO toExpenseVO(ExpenseEntity entity);
    List<ExpenseVO> toExpenseVOList(List<ExpenseEntity> entities);

    default PageResp<ExpenseVO> toExpenseVOPage(PageResp<ExpenseEntity> page) {
        return page == null ? null : page.map(this::toExpenseVO);
    }

    TaskVO toTaskVO(TaskEntity entity);
    List<TaskVO> toTaskVOList(List<TaskEntity> entities);

    default PageResp<TaskVO> toTaskVOPage(PageResp<TaskEntity> page) {
        return page == null ? null : page.map(this::toTaskVO);
    }

    IncomeVO toIncomeVO(IncomeEntity entity);
    List<IncomeVO> toIncomeVOList(List<IncomeEntity> entities);

    default PageResp<IncomeVO> toIncomeVOPage(PageResp<IncomeEntity> page) {
        return page == null ? null : page.map(this::toIncomeVO);
    }

    MbtiResultVO toMbtiResultVO(MbtiResultEntity entity);
    List<MbtiResultVO> toMbtiResultVOList(List<MbtiResultEntity> entities);

    default PageResp<MbtiResultVO> toMbtiResultVOPage(PageResp<MbtiResultEntity> page) {
        return page == null ? null : page.map(this::toMbtiResultVO);
    }

    TaskColumnVO toTaskColumnVO(TaskColumnEntity entity);
    List<TaskColumnVO> toTaskColumnVOList(List<TaskColumnEntity> entities);

    default PageResp<TaskColumnVO> toTaskColumnVOPage(PageResp<TaskColumnEntity> page) {
        return page == null ? null : page.map(this::toTaskColumnVO);
    }

    PasswordVaultVO toPasswordVaultVO(PasswordVaultEntity entity);
    List<PasswordVaultVO> toPasswordVaultVOList(List<PasswordVaultEntity> entities);

    default PageResp<PasswordVaultVO> toPasswordVaultVOPage(PageResp<PasswordVaultEntity> page) {
        return page == null ? null : page.map(this::toPasswordVaultVO);
    }

    TimeRecordListVO toTimeRecordListVO(TimeRecordEntity entity);
    List<TimeRecordListVO> toTimeRecordListVOList(List<TimeRecordEntity> entities);

    default PageResp<TimeRecordListVO> toTimeRecordListVOPage(PageResp<TimeRecordEntity> page) {
        return page == null ? null : page.map(this::toTimeRecordListVO);
    }

    UserBindVO toUserBindVO(UserBindEntity entity);
    List<UserBindVO> toUserBindVOList(List<UserBindEntity> entities);

    default PageResp<UserBindVO> toUserBindVOPage(PageResp<UserBindEntity> page) {
        return page == null ? null : page.map(this::toUserBindVO);
    }

    HonorCategoryVO toHonorCategoryVO(HonorCategoryEntity entity);
    List<HonorCategoryVO> toHonorCategoryVOList(List<HonorCategoryEntity> entities);

    default PageResp<HonorCategoryVO> toHonorCategoryVOPage(PageResp<HonorCategoryEntity> page) {
        return page == null ? null : page.map(this::toHonorCategoryVO);
    }

    ThoughtEventVO toThoughtEventVO(ThoughtRelaEventEntity entity);
    List<ThoughtEventVO> toThoughtEventVOList(List<ThoughtRelaEventEntity> entities);

    default PageResp<ThoughtEventVO> toThoughtEventVOPage(PageResp<ThoughtRelaEventEntity> page) {
        return page == null ? null : page.map(this::toThoughtEventVO);
    }

    ThoughtRecordVO toThoughtRecordVO(ThoughtEntity entity);
    List<ThoughtRecordVO> toThoughtRecordVOList(List<ThoughtEntity> entities);

    default PageResp<ThoughtRecordVO> toThoughtRecordVOPage(PageResp<ThoughtEntity> page) {
        return page == null ? null : page.map(this::toThoughtRecordVO);
    }

    SysDictDataRecordVO toSysDictDataRecordVO(SysDictDataEntity entity);
    List<SysDictDataRecordVO> toSysDictDataRecordVOList(List<SysDictDataEntity> entities);

    default PageResp<SysDictDataRecordVO> toSysDictDataRecordVOPage(PageResp<SysDictDataEntity> page) {
        return page == null ? null : page.map(this::toSysDictDataRecordVO);
    }

    TaskDetailVO toTaskDetailVO(TaskDetailEntity entity);
    List<TaskDetailVO> toTaskDetailVOList(List<TaskDetailEntity> entities);

    default PageResp<TaskDetailVO> toTaskDetailVOPage(PageResp<TaskDetailEntity> page) {
        return page == null ? null : page.map(this::toTaskDetailVO);
    }

    GoalVO toGoalVO(GoalEntity entity);
    List<GoalVO> toGoalVOList(List<GoalEntity> entities);

    default PageResp<GoalVO> toGoalVOPage(PageResp<GoalEntity> page) {
        return page == null ? null : page.map(this::toGoalVO);
    }

    @Mapping(target = "isHidden", expression = "java(java.util.Objects.equals(entity.getIsDeleted(), 1))")
    TimeTrackerCategoryVO toTimeTrackerCategoryVO(TimeTrackerCategoryEntity entity);
    List<TimeTrackerCategoryVO> toTimeTrackerCategoryVOList(List<TimeTrackerCategoryEntity> entities);

    default PageResp<TimeTrackerCategoryVO> toTimeTrackerCategoryVOPage(PageResp<TimeTrackerCategoryEntity> page) {
        return page == null ? null : page.map(this::toTimeTrackerCategoryVO);
    }

    ExerciseRecordVO toExerciseRecordVO(ExerciseRecordEntity entity);
    List<ExerciseRecordVO> toExerciseRecordVOList(List<ExerciseRecordEntity> entities);

    default PageResp<ExerciseRecordVO> toExerciseRecordVOPage(PageResp<ExerciseRecordEntity> page) {
        return page == null ? null : page.map(this::toExerciseRecordVO);
    }

    PerformanceVO toPerformanceVO(PerformanceEntity entity);
    List<PerformanceVO> toPerformanceVOList(List<PerformanceEntity> entities);

    default PageResp<PerformanceVO> toPerformanceVOPage(PageResp<PerformanceEntity> page) {
        return page == null ? null : page.map(this::toPerformanceVO);
    }

    MemoVO toMemoVO(MemoEntity entity);
    List<MemoVO> toMemoVOList(List<MemoEntity> entities);

    default PageResp<MemoVO> toMemoVOPage(PageResp<MemoEntity> page) {
        return page == null ? null : page.map(this::toMemoVO);
    }

    AnniversaryRecordVO toAnniversaryRecordVO(AnniversaryRecordEntity entity);
    List<AnniversaryRecordVO> toAnniversaryRecordVOList(List<AnniversaryRecordEntity> entities);

    default PageResp<AnniversaryRecordVO> toAnniversaryRecordVOPage(PageResp<AnniversaryRecordEntity> page) {
        return page == null ? null : page.map(this::toAnniversaryRecordVO);
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ThoughtRelaEventEntity fromThoughtEventUpdateReq(ThoughtEventUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MilestoneEntity fromMilestoneCreateReq(MilestoneCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MilestoneEntity fromMilestoneUpdateReq(MilestoneUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    DeviceEntity fromDeviceQuery(DeviceQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    DeviceEntity fromDeviceCreateReq(DeviceCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    DeviceEntity fromDeviceUpdateReq(DeviceUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserDictDataEntity fromUserDictDataQuery(UserDictDataQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserDictDataEntity fromUserDictDataCreateReq(UserDictDataCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserDictDataEntity fromUserDictDataUpdateReq(UserDictDataUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserDictDataEntity fromUserDictDataAdminCreateReq(UserDictDataAdminCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserDictDataEntity fromUserDictDataAdminUpdateReq(UserDictDataAdminUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    HonorRecordEntity fromHonorRecordCreateReq(HonorRecordCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    HonorRecordEntity fromHonorRecordUpdateReq(HonorRecordUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    SysDictTypeEntity fromSysDictTypeFilterQuery(SysDictTypeFilterQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    SysDictTypeEntity fromSysDictTypeCreateReq(SysDictTypeCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    SysDictTypeEntity fromSysDictTypeUpdateReq(SysDictTypeUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    BVideoEntity fromBVideoQuery(BVideoQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    BVideoEntity fromBVideoCreateReq(BVideoCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    BVideoEntity fromBVideoUpdateReq(BVideoUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    BVideoEntity fromBVideoProgressReq(BVideoProgressReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ExpenseEntity fromExpenseCreateReq(ExpenseCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ExpenseEntity fromExpenseUpdateReq(ExpenseUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskEntity fromTaskCreateReq(TaskCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskEntity fromTaskUpdateReq(TaskUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskEntity fromTaskSortReq(TaskSortReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    IncomeEntity fromIncomeCreateReq(IncomeCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    IncomeEntity fromIncomeUpdateReq(IncomeUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskColumnEntity fromTaskColumnQuery(TaskColumnQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskColumnEntity fromTaskColumnCreateReq(TaskColumnCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskColumnEntity fromTaskColumnUpdateReq(TaskColumnUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskColumnEntity fromTaskColumnSortReq(TaskColumnSortReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PasswordVaultEntity fromPasswordVaultCreateReq(PasswordVaultCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PasswordVaultEntity fromPasswordVaultUpdateReq(PasswordVaultUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TimeRecordEntity fromTimeRecordQuery(TimeRecordQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TimeRecordEntity fromTimeRecordDeleteByDateReq(TimeRecordDeleteByDateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserBindEntity fromUserBindCreateReq(UserBindCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserBindEntity fromUserBindUpdateReq(UserBindUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ThoughtEntity fromThoughtQuery(ThoughtQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ThoughtEntity fromThoughtUpdateReq(ThoughtUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    SysDictDataEntity fromSysDictDataCreateReq(SysDictDataCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    SysDictDataEntity fromSysDictDataUpdateReq(SysDictDataUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskDetailEntity fromTaskDetailSortReq(TaskDetailSortReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskDetailEntity fromTaskDetailCreateReq(TaskDetailCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TaskDetailEntity fromTaskDetailUpdateReq(TaskDetailUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    GoalEntity fromGoalCreateReq(GoalCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    GoalEntity fromGoalUpdateReq(GoalUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "parentId", ignore = true)
    TimeTrackerCategoryEntity fromTimeTrackerCategoryCreateReq(TimeTrackerCategoryCreateReq request);

    @AfterMapping
    default void preserveParentPresence(TimeTrackerCategoryCreateReq request, @MappingTarget TimeTrackerCategoryEntity entity) {
        if (request.isParentIdSpecified()) entity.setParentId(request.getParentId());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "parentId", ignore = true)
    TimeTrackerCategoryEntity fromTimeTrackerCategoryUpdateReq(TimeTrackerCategoryUpdateReq request);

    @AfterMapping
    default void preserveParentPresence(TimeTrackerCategoryUpdateReq request, @MappingTarget TimeTrackerCategoryEntity entity) {
        if (request.isParentIdSpecified()) entity.setParentId(request.getParentId());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TimeTrackerCategoryEntity fromTimeTrackerCategorySortReq(TimeTrackerCategorySortReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "parentId", ignore = true)
    TimeTrackerCategoryEntity fromTimeTrackerCategoryAdminCreateReq(TimeTrackerCategoryAdminCreateReq request);

    @AfterMapping
    default void preserveParentPresence(TimeTrackerCategoryAdminCreateReq request, @MappingTarget TimeTrackerCategoryEntity entity) {
        if (request.isParentIdSpecified()) entity.setParentId(request.getParentId());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "parentId", ignore = true)
    TimeTrackerCategoryEntity fromTimeTrackerCategoryAdminUpdateReq(TimeTrackerCategoryAdminUpdateReq request);

    @AfterMapping
    default void preserveParentPresence(TimeTrackerCategoryAdminUpdateReq request, @MappingTarget TimeTrackerCategoryEntity entity) {
        if (request.isParentIdSpecified()) entity.setParentId(request.getParentId());
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ExerciseRecordEntity fromExerciseRecordQuery(ExerciseRecordQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ExerciseRecordEntity fromExerciseRecordCreateReq(ExerciseRecordCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ExerciseRecordEntity fromExerciseRecordUpdateReq(ExerciseRecordUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PerformanceEntity fromPerformanceCreateReq(PerformanceCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    PerformanceEntity fromPerformanceUpdateReq(PerformanceUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MemoEntity fromMemoQuery(MemoQuery request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MemoEntity fromMemoCreateReq(MemoCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MemoEntity fromMemoUpdateReq(MemoUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    AnniversaryRecordEntity fromAnniversaryRecordCreateReq(AnniversaryRecordCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    AnniversaryRecordEntity fromAnniversaryRecordUpdateReq(AnniversaryRecordUpdateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    TimeRecordReq fromTimeRecordSaveReq(TimeRecordSaveReq request);
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    top.aiolife.record.pojo.req.ExerciseRecordReq fromTimeRecordExerciseReq(top.aiolife.record.pojo.req.TimeRecordExerciseReq request);
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "rating", ignore = true)
    MovieReq fromMovieCreateReq(MovieCreateReq request);
    @AfterMapping
    default void preserveRatingPresence(MovieCreateReq request, @MappingTarget MovieReq target) {
        if (request.isRatingProvided()) target.setRating(request.getRating());
    }
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    ReadRecordReq fromReadRecordCreateReq(ReadRecordCreateReq request);
    top.aiolife.record.pojo.vo.UserDictTypeVO toUserDictTypeVO(top.aiolife.record.pojo.entity.UserDictTypeEntity entity);
    top.aiolife.record.pojo.vo.TimeRecordRecommendationVO toTimeRecordRecommendationVO(top.aiolife.record.pojo.vo.RecommendNextVO result);
    TimeRecordSaveReq toTimeRecordSaveReq(TimeRecordReq request);
    top.aiolife.record.pojo.req.TimeRecordExerciseReq toTimeRecordExerciseReq(top.aiolife.record.pojo.req.ExerciseRecordReq request);
}

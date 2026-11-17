package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CBTI 人格管理端展示对象
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Data
public class CbtiPersonalityAdminVO {

    private Long id;

    private String code;

    private String name;

    private String motto;

    private String color;

    private List<Integer> vector;

    private String description;

    private List<String> strengths;

    private List<String> weaknesses;

    private String techStack;

    private String spirit;

    private String imageUrl;

    private Boolean isSpecial;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}


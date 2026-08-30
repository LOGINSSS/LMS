package com.lms.ai.tools;

import com.lms.ai.client.MediaClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 媒资工具组（spec §3.3：media-agent 工具面，Feign → lms-media）
 *
 * 上传：把文本内容（如生成的 HTML 讲义/Markdown）打包为文件上传到媒资服务，
 * 返回可直接访问的 url。
 */
@Component
@RequiredArgsConstructor
public class MediaTools {

    private final MediaClient client;

    @Tool(name = "upload", description = "上传文件（当前用户）。fileName 含扩展名（.md/.html/.txt/.png），content 为文本内容或 base64 图片数据；返回 MediaVO(id,name,type,url,size,mime)")
    public String upload(
            @ToolParam(name = "fileName", description = "文件名（含扩展名）") String fileName,
            @ToolParam(name = "content", description = "文件内容（文本或 base64）") String content,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "txt";
            String mediaType = switch (ext) {
                case "md", "markdown", "html", "htm", "txt" -> "text/plain";
                case "png", "jpg", "jpeg", "gif", "webp" -> "image/" + ("jpg".equals(ext) ? "jpeg" : ext);
                default -> "application/octet-stream";
            };
            byte[] bytes;
            if (content.startsWith("data:") || content.startsWith("base64,")) {
                bytes = java.util.Base64.getDecoder().decode(content.replaceFirst("^data:[^,]*,", "").replaceFirst("^base64,", ""));
            } else {
                bytes = content.getBytes(StandardCharsets.UTF_8);
            }
            MultipartFile file = new SimpleMultipartFile(fileName, mediaType, bytes);
            return ToolSupport.json(ToolSupport.check(client.upload(file)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "page", description = "我的文件分页（type: 1图片 2视频 3其他）", readOnly = true)
    public String page(
            @ToolParam(name = "type", description = "类型：1图片 2视频 3其他", required = false) Integer type,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认20", required = false) Integer pageSize,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.page(type, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "getDetail", description = "文件详情", readOnly = true)
    public String getDetail(@ToolParam(name = "id", description = "文件 id") Long id) {
        try {
            return ToolSupport.json(ToolSupport.check(client.getDetail(id)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "delete", description = "删除文件（仅本人）")
    public String delete(@ToolParam(name = "id", description = "文件 id") Long id, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.delete(id));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}

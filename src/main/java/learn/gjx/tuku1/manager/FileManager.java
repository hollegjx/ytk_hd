package learn.gjx.tuku1.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

/**
 * 文件管理（封装 CosManager，增加文件解析功能）
 */
@Component
@Slf4j
public class FileManager {

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 上传图片并解析图片信息（使用 COS 数据万象解析）
     *
     * @param key      对象键
     * @param file     本地文件
     * @param fileName 原始文件名
     * @return 图片上传结果（包含解析信息）
     */
    public PictureUploadResult uploadPicture(String key, File file, String fileName) {
        // 上传图片并获取图片信息
        PutObjectResult putObjectResult = cosManager.putPictureObject(key, file);

        // 从 COS 返回结果中解析图片信息
        ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

        PictureUploadResult result = new PictureUploadResult();
        result.setUrl(getFileUrl(key));
        result.setPicName(FileUtil.mainName(fileName));
        result.setPicSize(FileUtil.size(file));
        result.setPicFormat(imageInfo.getFormat());
        result.setPicWidth(imageInfo.getWidth());
        result.setPicHeight(imageInfo.getHeight());
        // 计算宽高比（保留两位小数）
        result.setPicScale((double) imageInfo.getWidth() / imageInfo.getHeight());

        return result;
    }

    /**
     * 获取文件访问 URL
     *
     * @param key 对象键
     * @return 完整访问路径
     */
    public String getFileUrl(String key) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosClientConfig.getBucket(), cosClientConfig.getRegion(), key);
    }
}

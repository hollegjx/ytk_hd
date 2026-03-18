package learn.gjx.tuku1.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRule;
import learn.gjx.tuku1.model.vo.PictureProcessInfoVO;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * File manager that wraps {@link CosManager} and adds validation, path generation,
 * and Tencent Cloud CI (图片处理) helper utilities.
 */
@Component
@Slf4j
public class FileManager {

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * Upload an image after running validation, generating a COS key, and parsing CI metadata.
     *
     * @param multipartFile    binary file provided by the client
     * @param uploadPathPrefix prefix such as {@code public/{userId}}
     * @return upload result enriched with width, height, size, etc.
     */
    public PictureUploadResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // Validate basic file constraints (size, suffix, non-null)
        validatePicture(multipartFile);

        // Keep the original filename for display purposes
        String originalFilename = multipartFile.getOriginalFilename();

        // Generate an upload name: date + random suffix + original extension
        String uuid = RandomUtil.randomString(16);
        String uploadFilename = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()),
                uuid,
                FileUtil.getSuffix(originalFilename));

        // Compose the final COS key
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);

        File tempFile = null;
        try {
            // Save MultipartFile to a temporary file because COS SDK works with File objects
            tempFile = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(tempFile);

            // Upload to COS and retrieve CI metadata
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);

            // Parse width/height/ratio from the CI response
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

            // Build DTO for upstream layers
            PictureUploadResult result = new PictureUploadResult();
            result.setUrl(getFileUrl(uploadPath));
            result.setPicName(FileUtil.mainName(originalFilename));
            result.setPicSize(multipartFile.getSize());
            result.setPicFormat(imageInfo.getFormat());
            result.setPicWidth(picWidth);
            result.setPicHeight(picHeight);
            result.setPicScale(picScale);

            return result;
        } catch (IOException e) {
            log.error("Picture upload failed, originalFilename: {}", originalFilename, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            // Ensure temp file is removed
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Build the public COS URL for the given object key.
     */
    public String getFileUrl(String key) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosClientConfig.getBucket(), cosClientConfig.getRegion(), key);
    }

    /**
     * Extract the COS object key from a previously generated URL.
     */
    public String extractKeyFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String httpsPrefix = String.format("https://%s.cos.%s.myqcloud.com/", cosClientConfig.getBucket(), cosClientConfig.getRegion());
        if (StrUtil.startWith(url, httpsPrefix)) {
            return url.substring(httpsPrefix.length());
        }
        String httpPrefix = String.format("http://%s.cos.%s.myqcloud.com/", cosClientConfig.getBucket(), cosClientConfig.getRegion());
        if (StrUtil.startWith(url, httpPrefix)) {
            return url.substring(httpPrefix.length());
        }
        return url;
    }

    /**
     * Basic validation for uploaded images.
     */
    private void validatePicture(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        long maxSize = 2 * 1024 * 1024L;
        if (multipartFile.getSize() > maxSize) {
            log.error("File size exceeds limit, size: {}", multipartFile.getSize());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
        }
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        List<String> allowedSuffixes = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
        if (!allowedSuffixes.contains(suffix.toLowerCase())) {
            log.error("Unsupported image format, suffix: {}", suffix);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片格式");
        }
    }

    /**
     * Apply Tencent Cloud CI processing rules to an existing COS object.
     *
     * @param sourceKey original COS key
     * @param ruleList  list of processing rules (rotate/90, thumbnail/!200x200r, etc.)
     * @param outputDir directory for processed objects, defaults to {@code processed}
     */
    public PictureProcessResult processPicture(String sourceKey, List<PictureProcessRule> ruleList, String outputDir) {
        if (StrUtil.isBlank(sourceKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原图路径不能为空");
        }
        List<PictureProcessRule> validRules = CollUtil.emptyIfNull(ruleList).stream()
                .filter(rule -> rule != null && StrUtil.isNotBlank(rule.getRule()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(validRules)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请至少提供一条处理规则");
        }
        if (validRules.size() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "处理规则不能超过 5 条");
        }

        File tempFile = null;
        try {
            tempFile = copyRemoteFileToTemp(sourceKey);
            PicOperations picOperations = buildPicOperations(validRules, outputDir, FileUtil.getSuffix(sourceKey));
            String uploadKey = buildProcessUploadKey(sourceKey);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadKey, tempFile, picOperations);
            return buildProcessResult(putObjectResult);
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private File copyRemoteFileToTemp(String key) {
        COSObject cosObject = cosManager.getObject(key);
        if (cosObject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "原图不存在");
        }
        File tempFile;
        try {
            tempFile = File.createTempFile("process_", "." + FileUtil.getSuffix(key));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建临时文件失败");
        }
        try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            FileUtil.writeFromStream(inputStream, tempFile);
        } catch (IOException e) {
            log.error("原图下载失败, key: {}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "拉取图片数据失败");
        }
        return tempFile;
    }

    private PicOperations buildPicOperations(List<PictureProcessRule> rules, String outputDir, String originalSuffix) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> cosRules = new ArrayList<>();
        String targetDir = StrUtil.isBlank(outputDir) ? "processed" : StrUtil.removeSuffix(outputDir.trim(), "/");
        targetDir = StrUtil.removePrefix(targetDir, "/");
        if (StrUtil.isBlank(targetDir)) {
            targetDir = "processed";
        }
        for (int i = 0; i < rules.size(); i++) {
            PictureProcessRule rule = rules.get(i);
            PicOperations.Rule cosRule = new PicOperations.Rule();
            cosRule.setBucket(cosClientConfig.getBucket());
            cosRule.setRule(rule.getRule());
            cosRule.setFileId(buildOutputFileId(targetDir, rule, i, originalSuffix));
            cosRules.add(cosRule);
        }
        picOperations.setRules(cosRules);
        return picOperations;
    }

    private String buildOutputFileId(String targetDir, PictureProcessRule rule, int index, String originalSuffix) {
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(rule.getOutputFileName()), originalSuffix);
        if (StrUtil.isBlank(suffix)) {
            suffix = "jpg";
        }
        String fileName;
        if (StrUtil.isNotBlank(rule.getOutputFileName())) {
            fileName = rule.getOutputFileName();
            if (!fileName.contains(".")) {
                fileName = fileName + "." + suffix;
            }
        } else {
            fileName = String.format("%s_%s_%d.%s",
                    DateUtil.format(new Date(), "yyyyMMddHHmmss"),
                    RandomUtil.randomString(6),
                    index,
                    suffix);
        }
        return String.format("%s/%s", targetDir, fileName);
    }

    private String buildProcessUploadKey(String sourceKey) {
        String suffix = FileUtil.getSuffix(sourceKey);
        if (StrUtil.isBlank(suffix)) {
            suffix = "tmp";
        }
        return String.format("processing/%s_%s.%s",
                DateUtil.format(new Date(), "yyyyMMddHHmmss"),
                RandomUtil.randomString(8),
                suffix);
    }

    private PictureProcessResult buildProcessResult(PutObjectResult putObjectResult) {
        PictureProcessResult result = new PictureProcessResult();
        if (putObjectResult == null) {
            return result;
        }
        CIUploadResult ciUploadResult = putObjectResult.getCiUploadResult();
        if (ciUploadResult == null) {
            return result;
        }
        result.setOriginal(PictureProcessInfoVO.fromOriginal(ciUploadResult.getOriginalInfo()));
        ProcessResults processResults = ciUploadResult.getProcessResults();
        if (processResults != null && CollUtil.isNotEmpty(processResults.getObjectList())) {
            for (CIObject ciObject : processResults.getObjectList()) {
                result.addProcessedInfo(PictureProcessInfoVO.fromProcessObject(ciObject));
            }
        }
        return result;
    }
}

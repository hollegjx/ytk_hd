package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import learn.gjx.tuku1.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;

/**
 * 对腾讯云 COS 客户端的简单封装，统一处理常见的对象操作。
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 以指定的对象键上传本地文件。
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传图片并启用数据万象，获取宽高等元信息。
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        return putPictureObject(key, file, picOperations);
    }

    /**
     * 上传图片并附带自定义的数据万象处理规则。
     */
    public PutObjectResult putPictureObject(String key, File file, PicOperations picOperations) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, file);
        if (picOperations != null) {
            putObjectRequest.setPicOperations(picOperations);
        }
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 通过输入流上传对象。
     */
    public PutObjectResult putObject(String key, InputStream inputStream, ObjectMetadata metadata) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, inputStream, metadata);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 根据 key 下载对象。
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(
                cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 根据 key 删除对象。
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}

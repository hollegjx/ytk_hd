package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import learn.gjx.tuku1.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;

/**
 * 腾讯云 COS 对象存储操作
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象（文件）
     *
     * @param key  对象键（唯一标识，例如：images/picture.jpg）
     * @param file 本地文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传对象（输入流）
     *
     * @param key         对象键
     * @param inputStream 输入流
     * @param metadata    对象元数据（可设置 Content-Type、Content-Length 等）
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, InputStream inputStream, ObjectMetadata metadata) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, inputStream, metadata);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     *
     * @param key 对象键
     * @return COS 对象（包含输入流和元数据）
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(
                cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 对象键
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}

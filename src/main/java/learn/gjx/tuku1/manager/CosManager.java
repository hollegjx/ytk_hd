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
 * Thin wrapper around the Tencent COS client that exposes common object operations.
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * Upload a local file as the specified object key.
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * Upload an image while enabling CI to return metadata (width/height/etc.).
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        return putPictureObject(key, file, picOperations);
    }

    /**
     * Upload an image with custom CI processing rules.
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
     * Upload an object from an input stream.
     */
    public PutObjectResult putObject(String key, InputStream inputStream, ObjectMetadata metadata) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, inputStream, metadata);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * Download an object.
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(
                cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * Delete an object by key.
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}

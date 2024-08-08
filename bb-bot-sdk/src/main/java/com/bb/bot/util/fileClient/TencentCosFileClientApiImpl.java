package com.bb.bot.util.fileClient;

import com.bb.bot.util.RestUtils;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.StorageClass;
import com.qcloud.cos.model.UploadResult;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Upload;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 腾讯对象存储的图片上传工具
 * 未实现删除
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix="fileClient",name = "type", havingValue = "tencentCos", matchIfMissing = false)
public class TencentCosFileClientApiImpl implements FileClientApi {

    @Autowired
    private TransferManager transferManager;

    @Autowired
    private COSClient cosClient;

    /**
     * 存储桶的命名格式为 BucketName-APPID，此处填写的存储桶名称必须为此格式
     */
    @Value("${tencent.cos.bucketName:misutmp-1312130478}")
    private String bucketName;

    @Autowired
    private RestUtils restUtils;

    @Override
    public String uploadTmpFile(InputStream inputStream) {
        return uploadFile(inputStream, "tmp/" + System.currentTimeMillis() + ".png");
    }

    @Override
    @SneakyThrows
    public String uploadFile(InputStream inputStream, String remotePath) {
        // 对象键(Key)是对象在存储桶中的唯一标识。
        String key = remotePath;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(getSize(inputStream));
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);

        // 设置存储类型（如有需要，不需要请忽略此行代码）, 默认是标准(Standard), 低频(standard_ia)
        // 更多存储类型请参见 https://cloud.tencent.com/document/product/436/33417
        putObjectRequest.setStorageClass(StorageClass.Standard_IA);

        //若需要设置对象的自定义 Headers 可参照下列代码,若不需要可省略下面这几行,对象自定义 Headers 的详细信息可参考 https://cloud.tencent.com/document/product/436/13361
        ObjectMetadata objectMetadata = new ObjectMetadata();

        //若设置 Content-Type、Cache-Control、Content-Disposition、Content-Encoding、Expires 这五个字自定义 Headers，推荐采用 objectMetadata.setHeader()
//        objectMetadata.setHeader(key, value);
        objectMetadata.setExpirationTime(Date.from(LocalDateTime.now().plusMinutes(5).atZone(ZoneId.systemDefault()).toInstant()));
        //若要设置 “x-cos-meta-[自定义后缀]” 这样的自定义 Header，推荐采用
//        Map<String, String> userMeta = new HashMap<String, String>();
//        userMeta.put("x-cos-meta-[自定义后缀]", "value");
//        objectMetadata.setUserMetadata(userMeta);

        putObjectRequest.withMetadata(objectMetadata);

        try {
            // 高级接口会返回一个异步结果Upload
            // 可同步地调用 waitForUploadResult 方法等待上传完成，成功返回 UploadResult, 失败抛出异常
            Upload upload = transferManager.upload(putObjectRequest);
            UploadResult uploadResult = upload.waitForUploadResult();

            // 获取返回的url
            return cosClient.getObjectUrl(bucketName, key).toString();
        } catch (CosServiceException e) {
            e.printStackTrace();
        } catch (CosClientException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "";
    }

    @Override
    public InputStream downloadFile(String remotePath) {
        return restUtils.getFileInputStream(remotePath);
    }

    @Override
    public void deleteFile(String remotePath) {

    }

    @Override
    public void deleteTmpFile() {

    }

    static long getSize(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        long size = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            size += bytesRead;
        }

        return size;
    }
}

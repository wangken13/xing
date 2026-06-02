package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VideoTask {

    @Autowired
    MediaFileService mediaFileService;
    @Autowired
    MediaFileProcessService mediaFileProcessService;

    @Value("${videoprocess.ffmpegpath}")
    String ffmpegpath;

    @XxlJob("videoJobHandler")
    public void videoJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        List<MediaProcess> mediaProcessList = null;
        int size = 0;
        try {
            // 取出cpu核心数作为一次处理数据的条数
            int processors = Runtime.getRuntime().availableProcessors();
            // 一次处理视频数量不要超过cpu核心数
            mediaProcessList = mediaFileProcessService.getMediaProcessList(shardIndex, shardTotal, processors);
            size = mediaProcessList.size();
            log.debug("取出待处理视频任务{}条", size);
            if (size <= 0) {
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // 启动size个线程的线程池
        ExecutorService threadPool = Executors.newFixedThreadPool(size);
        CountDownLatch countDownLatch = new CountDownLatch(size);

        // 将处理任务加入线程池
        mediaProcessList.forEach(mediaProcess -> {
            threadPool.execute(() -> {
                try {
                    // 任务id
                    Long taskId = mediaProcess.getId();
                    // 抢占任务
                    boolean b = mediaFileProcessService.startTask(taskId);
                    if (!b) {
                        return;
                    }
                    log.debug("开始执行任务:{}", mediaProcess);

                    String bucket = mediaProcess.getBucket();
                    String filePath = mediaProcess.getFilePath();
                    String fileId = mediaProcess.getFileId();
                    String filename = mediaProcess.getFilename();

                    // 1️⃣ 下载待处理文件
                    File originalFile = mediaFileService.downloadFileFromMinIO(
                            mediaProcess.getBucket(),
                            mediaProcess.getFilePath()
                    );
                    if (originalFile == null) {
                        log.debug("下载待处理文件失败,originalFile:{}",
                                mediaProcess.getBucket().concat(mediaProcess.getFilePath()));
                        mediaFileProcessService.saveProcessFinishStatus(
                                mediaProcess.getId(), "3", fileId, null, "下载待处理文件失败"
                        );
                        return;
                    }

                    // 2️⃣ 创建临时mp4文件
                    File mp4File = null;
                    try {
                        mp4File = File.createTempFile("mp4", ".mp4");
                    } catch (IOException e) {
                        log.error("创建mp4临时文件失败");
                        mediaFileProcessService.saveProcessFinishStatus(
                                mediaProcess.getId(), "3", fileId, null, "创建mp4临时文件失败"
                        );
                        return;
                    }

                    // 3️⃣ 视频转码处理
                    String result = "";
                    try {
                        Mp4VideoUtil videoUtil = new Mp4VideoUtil(
                                ffmpegpath,
                                originalFile.getAbsolutePath(),
                                mp4File.getName(),
                                mp4File.getAbsolutePath()
                        );
                        result = videoUtil.generateMp4();
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("处理视频文件:{},出错:{}", mediaProcess.getFilePath(), e.getMessage());
                    }

                    if (!"success".equals(result)) {
                        log.error("处理视频失败,视频地址:{},错误信息:{}", bucket + filePath, result);
                        mediaFileProcessService.saveProcessFinishStatus(
                                mediaProcess.getId(), "3", fileId, null, result
                        );
                        return;
                    }

                    // 4️⃣ 上传 mp4 到 MinIO
                    String objectName = getFilePath(fileId, ".mp4");
                    String url = "/" + bucket + "/" + objectName;
                    try {
                        mediaFileService.addMediaFilesToMinIO(
                                mp4File.getAbsolutePath(),
                                "video/mp4",
                                bucket,
                                objectName
                        );
                        mediaFileProcessService.saveProcessFinishStatus(
                                mediaProcess.getId(), "2", fileId, url, null
                        );
                    } catch (Exception e) {
                        log.error("上传视频失败或入库失败,视频地址:{},错误信息:{}",
                                bucket + objectName, e.getMessage());
                        mediaFileProcessService.saveProcessFinishStatus(
                                mediaProcess.getId(), "3", fileId, null, "处理后视频上传或入库失败"
                        );
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
        });

        // 等待，超时时间 30 分钟
        countDownLatch.await(30, TimeUnit.MINUTES);
    }

    private String getFilePath(String fileMd5, String fileExt) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + fileExt;
    }
}
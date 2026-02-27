package com.bb.bot.common.util;

import com.bb.bot.config.FilePathConfig;
import lombok.SneakyThrows;
import java.util.Base64;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件处理工具类
 * 
 * @author rym
 */
public class FileUtils {
    public static String FILENAME_PATTERN = "[a-zA-Z0-9_\\-\\|\\.\\u4e00-\\u9fa5]+";

    /**
     * 生成临时文件
     * @return File 文件
     */
    public static File buildTmpFile() {
        return new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
    }

    /**
     * 获取文件资源目录的绝对路径
     *
     * @param subPath 相对路径
     * @return File 文件
     */
    public static String getAbsolutePath(String subPath) {
        String absolutePath = subPath;

        if (!subPath.substring(0, 1).equals("/")) {
            //如果第一个路径不是/号，则从相对路径取文件
            absolutePath = SpringUtils.getBean(FilePathConfig.class).getFilePath() + subPath;
        }
        return absolutePath;
    }

    /**
     * 获取相对目录下的随机文件
     *
     * @param folderPath 文件夹路径
     * @return File 文件
     */
    public static File getRandomFileFromFolder(String folderPath) {
        List<String> allFilePathList = getAllFilePath(folderPath);
        if (allFilePathList != null && allFilePathList.size() > 0) {
            return new File(allFilePathList.get((int) (Math.random()* allFilePathList.size())));
        }else {
            return null;
        }
    }

    /**
     * 获取相对目录下的随机一个文件的Base64字符串
     *
     * @param folderPath 文件夹路径
     * @return String 文件Base64字符串
     */
    public static String getRandomFileBase64FromFolder(String folderPath) {
        List<String> allFilePathList = getAllFilePath(folderPath);
        if (allFilePathList != null && allFilePathList.size() > 0) {
            return fileToBase64(allFilePathList.get((int) (Math.random()* allFilePathList.size())));
        }else {
            return null;
        }
    }

    /**
     * 获取相对目录下的所有文件路径
     *
     * @param folderPath 文件路径
     * @return String 文件Base64字符串
     */
    public static List<String> getAllFilePath(String folderPath) {
        String absolutePath = SpringUtils.getBean(FilePathConfig.class).getFilePath() + folderPath;
        File folder = new File(absolutePath);

        List<String> filePathList = new ArrayList<>();
        String rootPath;
        if (folder.exists()) {
            String[] fileNameList = folder.list();
            if (null != fileNameList && fileNameList.length > 0) {
                if (folder.getPath().endsWith(File.separator)) {
                    rootPath = folder.getPath();
                } else {
                    rootPath = folder.getPath() + File.separator;
                }
                for (String fileName : fileNameList) {
                    filePathList.add("/" + rootPath + fileName);
                }
            }
        }
        return filePathList;
    }

    /**
     * 从指定路径获取文件并转为base64字符串
     *
     * @param filePath 文件路径
     * @return String 文件Base64字符串
     */
    public static String fileToBase64(String filePath) {
        return Base64.getEncoder().encodeToString(getFile(filePath));
    }

    /**
     * 将文件转base64字符串
     * @param file  文件
     * @return String 文件Base64字符串
     */
    public static String fileToBase64(File file) {
        String base64 = null;
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            in.read(bytes);
            base64 = new String(Base64.getEncoder().encode(bytes),"UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return base64;
    }

    /**
     * 将输入转base64字符串
     * @param in  输入流
     * @return String 文件Base64字符串
     */
    public static String InputStreamToBase64(InputStream in) {
        String base64 = null;
        try {
            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            base64 = new String(Base64.getEncoder().encode(bytes),"UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return base64;
    }

    /**
     * 获取指定路径的文件数据
     *
     * @param filePath 文件路径
     * @return byte[] 文件
     */
    @SneakyThrows
    public static byte[] getFile(String filePath) {
        InputStream fileInputStream = null;
        byte[] data = null;
        try {
            if (!filePath.substring(0, 1).equals("/")) {
                //如果第一个路径不是/号，则从相对路径取文件
                String absolutePath = getAbsolutePath(filePath);
                fileInputStream = new FileInputStream(absolutePath);
            }else {
                //否则从绝对路径读取文件
                fileInputStream = new FileInputStream(filePath);
            }

            if (fileInputStream != null) {
                data = new byte[fileInputStream.available()];
                fileInputStream.read(data);
                fileInputStream.close();
            }

        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        }

        return data;
    }

    /**
     * 将一个文件数据复制目标文件中
     *
     * @param source 数据
     * @param dest 目标文件
     */
    @SneakyThrows
    public static void copyFileUsingFileStreams(File source, File dest) {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }
        } finally {
            input.close();
            output.close();
        }
    }

    /**
     * 如果目标文件不存在，获取指定路径的文件数据并保存到目标文件中
     *
     * @param source 数据
     * @param dest 目标文件
     */
    @SneakyThrows
    public static void writeSourceFileToDestFile(String source, String dest) {
        File destFile = new File(dest);
        //判断目标文件是否存在
        if (!destFile.exists()) {
            //判断目录是否存在，不存在则创建
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            destFile.createNewFile();
            //获取源文件
            byte[] data = getFile(source);
            try {
                //写入数据到目标文件
                writeBytes(data, dest);
            }catch (Exception e) {
                //写入失败要删除文件
                File file = new File(dest);
                file.delete();
                throw e;
            }
        }
    }

    /**
     * 写数据到文件中
     *
     * @param data 数据
     * @param uploadDir 目标文件
     * @return 目标文件
     * @throws IOException IO异常
     */
    public static String writeBytes(byte[] data, String uploadDir) throws IOException {
        File file = new File(uploadDir);
        try (
                FileOutputStream fos = new FileOutputStream(file);){
            fos.write(data);
            return file.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除指定目录下的所有文件
     *
     * @param file 文件
     * @return
     */
    public static void deleteAllFileFromFolder(File file){
        //判断文件不为null或文件目录存在
        if (file == null || !file.exists()){
            System.out.println("文件删除失败,请检查文件路径是否正确");
            return;
        }
        //取得这个目录下的所有子文件对象
        File[] files = file.listFiles();
        //遍历该目录下的文件对象
        for (File f: files){
            //判断子目录是否存在子目录,如果是文件则删除
            if (f.isDirectory()){
                deleteAllFileFromFolder(f);
            }else {
                f.delete();
            }
        }
    }

    /**
     * 删除文件
     * 
     * @param filePath 文件
     * @return
     */
    public static boolean deleteFile(String filePath)
    {
        boolean flag = false;
        File file = new File(filePath);
        // 路径为文件且不为空则进行删除
        if (file.isFile() && file.exists())
        {
            flag = file.delete();
        }
        return flag;
    }

    /**
     * 获取图像后缀
     * 
     * @param photoByte 图像数据
     * @return 后缀名
     */
    public static String getFileExtendName(byte[] photoByte) {
        String strFileExtendName = "jpg";
        if ((photoByte[0] == 71) && (photoByte[1] == 73) && (photoByte[2] == 70) && (photoByte[3] == 56)
                && ((photoByte[4] == 55) || (photoByte[4] == 57)) && (photoByte[5] == 97)) {
            strFileExtendName = "gif";
        } else if ((photoByte[6] == 74) && (photoByte[7] == 70) && (photoByte[8] == 73) && (photoByte[9] == 70)) {
            strFileExtendName = "jpg";
        } else if ((photoByte[0] == 66) && (photoByte[1] == 77)) {
            strFileExtendName = "bmp";
        } else if ((photoByte[1] == 80) && (photoByte[2] == 78) && (photoByte[3] == 71)) {
            strFileExtendName = "png";
        }
        return strFileExtendName;
    }

    /**
     * 获取文件名称 /profile/upload/2022/04/16/ruoyi.png -- ruoyi.png
     * 
     * @param fileName 路径名称
     * @return 没有文件路径的名称
     */
    public static String getName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int lastUnixPos = fileName.lastIndexOf('/');
        int lastWindowsPos = fileName.lastIndexOf('\\');
        int index = Math.max(lastUnixPos, lastWindowsPos);
        return fileName.substring(index + 1);
    }
}

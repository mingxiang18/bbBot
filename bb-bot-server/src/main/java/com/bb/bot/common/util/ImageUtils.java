package com.bb.bot.common.util;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.*;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.bb.bot.util.FileUtils;
import lombok.SneakyThrows;
import sun.font.FontDesignMetrics;

/*
 * 图片工具类
 **/
public class ImageUtils{

    /**
     * 对图片裁剪，并把裁剪新图片保存
     * @param srcFile 读取源图片路径
     * @param toFile	写入图片路径
     * @param x 剪切起始点x坐标
     * @param y 剪切起始点y坐标
     * @param width 剪切宽度
     * @param height	 剪切高度
     * @throws IOException
     */
    @SneakyThrows
    public static void cropImage(File srcFile,File toFile, int x, int y, int width, int height) {
        FileInputStream fis = null ;
        ImageInputStream iis =null ;
        try{
            //读取图片文件
            fis = new FileInputStream(srcFile);
            Iterator it = ImageIO.getImageReadersByFormatName(srcFile.getName().substring(srcFile.getName().lastIndexOf(".") + 1));
            ImageReader reader = (ImageReader) it.next();
            //获取图片流
            iis = ImageIO.createImageInputStream(fis);
            reader.setInput(iis,true) ;
            ImageReadParam param = reader.getDefaultReadParam();
            //定义一个矩形
            Rectangle rect = new Rectangle(x, y, width, height);
            //提供一个 BufferedImage，将其用作解码像素数据的目标。
            param.setSourceRegion(rect);
            BufferedImage bi = reader.read(0,param);
            //保存新图片
            ImageIO.write(bi, toFile.getName().substring(toFile.getName().lastIndexOf(".") + 1), toFile);
        }finally{
            if(fis!=null) {
                fis.close();
            }
            if(iis!=null) {
                iis.close();
            }
        }
    }

    /**
     * 等比例缩放图片
     * @param srcImagePath 读取图形路径
     * @param toImagePath 写入入行路径
     * @param ratio	放大比例
     * @throws IOException
     */
    public static void enlargementImageEqualProportion(String srcImagePath,String toImagePath,double ratio) throws IOException{
        try{
            //读入文件
            File file = new File(srcImagePath);
            // 构造Image对象
            BufferedImage src = ImageIO.read(file);
            int width = src.getWidth();
            int height = src.getHeight();

            int afterWidth = (int) (width * ratio);
            int afterHeight = (int) (height * ratio);
            // 放大边长
            BufferedImage tag = new BufferedImage(afterWidth, afterHeight, BufferedImage.TYPE_INT_RGB);
            //绘制放大后的图片
            Graphics2D g = tag.createGraphics();

            // 下面两行解决png透明图片会变黑的问题
            tag = g.getDeviceConfiguration().createCompatibleImage(tag.getWidth(null), tag.getHeight(null), Transparency.TRANSLUCENT);
            g = tag.createGraphics();

            g.drawImage(src, 0, 0, afterWidth, afterHeight, null);
            File out = new File(toImagePath);
            //保存新图片
            ImageIO.write(tag, out.getName().substring(out.getName().lastIndexOf(".") + 1), out);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 重置图形的边长大小
     * @param srcImagePath
     * @param toImagePath
     * @param width
     * @param height
     * @throws IOException
     */
    public static void resizeImage(String srcImagePath, String toImagePath, int width, int height) throws IOException{
        try{
            //读入文件
            File file = new File(srcImagePath);
            // 构造Image对象
            BufferedImage src = ImageIO.read(file);
            // 放大边长
            BufferedImage tag = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            //绘制放大后的图片
            tag.getGraphics().drawImage(src, 0, 0, width, height, null);
            File out = new File(toImagePath);
            //保存新图片
            ImageIO.write(tag, out.getName().substring(out.getName().lastIndexOf(".") + 1), out);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 横向拼接图片（两张）
     * @param firstSrcImagePath 第一张图片的路径
     * @param secondSrcImagePath	第二张图片的路径
     * @param imageFormat	拼接生成图片的格式
     * @param toPath	拼接生成图片的路径
     */
    public static void joinImagesHorizontal(String firstSrcImagePath, String secondSrcImagePath,String imageFormat, String toPath){
        try {
            //读取第一张图片
            File  fileOne  =  new  File(firstSrcImagePath);
            BufferedImage  imageOne = ImageIO.read(fileOne);
            int  width  =  imageOne.getWidth();//图片宽度
            int  height  =  imageOne.getHeight();//图片高度
            //从图片中读取RGB
            int[]  imageArrayOne  =  new  int[width*height];
            imageArrayOne  =  imageOne.getRGB(0,0,width,height,imageArrayOne,0,width);

            //对第二张图片做相同的处理
            File  fileTwo  =  new  File(secondSrcImagePath);
            BufferedImage  imageTwo  =  ImageIO.read(fileTwo);
            int width2 = imageTwo.getWidth();
            int height2 = imageTwo.getHeight();
            int[]   ImageArrayTwo  =  new  int[width2*height2];
            ImageArrayTwo  =  imageTwo.getRGB(0,0,width,height,ImageArrayTwo,0,width);
            //ImageArrayTwo  =  imageTwo.getRGB(0,0,width2,height2,ImageArrayTwo,0,width2);

            //生成新图片
            //int height3 = (height>height2 || height==height2)?height:height2;
            BufferedImage  imageNew  =  new  BufferedImage(width*2,height,BufferedImage.TYPE_INT_RGB);
            //BufferedImage  imageNew  =  new  BufferedImage(width+width2,height3,BufferedImage.TYPE_INT_RGB);
            imageNew.setRGB(0,0,width,height,imageArrayOne,0,width);//设置左半部分的RGB
            imageNew.setRGB(width,0,width,height,ImageArrayTwo,0,width);//设置右半部分的RGB
            //imageNew.setRGB(width,0,width2,height2,ImageArrayTwo,0,width2);//设置右半部分的RGB

            File  outFile  =  new  File(toPath);
            ImageIO.write(imageNew,  imageFormat,  outFile);//写图片
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 横向拼接一组（多张）图像
     * @param pics  将要拼接的图像
     * @param type 图像写入格式
     * @param dst_pic 图像写入路径
     * @return
     */
    public static boolean joinImageListHorizontal(String[] pics, String type, String dst_pic) {
        try {
            int len = pics.length;
            if (len < 1) {
                System.out.println("pics len < 1");
                return false;
            }
            File[] src = new File[len];
            BufferedImage[] images = new BufferedImage[len];
            int[][] imageArrays = new int[len][];
            for (int i = 0; i < len; i++) {
                src[i] = new File(pics[i]);
                images[i] = ImageIO.read(src[i]);
                int width = images[i].getWidth();
                int height = images[i].getHeight();
                imageArrays[i] = new int[width * height];// 从图片中读取RGB
                imageArrays[i] = images[i].getRGB(0, 0, width, height,  imageArrays[i], 0, width);
            }

            int dst_width = 0;
            int dst_height = images[0].getHeight();
            for (int i = 0; i < images.length; i++) {
                dst_height = dst_height > images[i].getHeight() ? dst_height : images[i].getHeight();
                dst_width += images[i].getWidth();
            }
            //System.out.println(dst_width);
            //System.out.println(dst_height);
            if (dst_height < 1) {
                System.out.println("dst_height < 1");
                return false;
            }
            /*
             * 生成新图片
             */
            BufferedImage ImageNew = new BufferedImage(dst_width, dst_height,  BufferedImage.TYPE_INT_RGB);
            int width_i = 0;
            for (int i = 0; i < images.length; i++) {
                ImageNew.setRGB(width_i, 0, images[i].getWidth(), dst_height,  imageArrays[i], 0, images[i].getWidth());
                width_i += images[i].getWidth();
            }
            File outFile = new File(dst_pic);
            ImageIO.write(ImageNew, type, outFile);// 写图片
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 纵向拼接图片（两张）
     * @param firstSrcImagePath 读取的第一张图片
     * @param secondSrcImagePath	读取的第二张图片
     * @param imageFormat 图片写入格式
     * @param toPath	图片写入路径
     */
    public static void joinImagesVertical(String firstSrcImagePath, String secondSrcImagePath,String imageFormat, String toPath){
        try {
            //读取第一张图片
            File  fileOne  =  new  File(firstSrcImagePath);
            BufferedImage  imageOne = ImageIO.read(fileOne);
            int  width  =  imageOne.getWidth();//图片宽度
            int  height  =  imageOne.getHeight();//图片高度
            //从图片中读取RGB
            int[]  imageArrayOne  =  new  int[width*height];
            imageArrayOne  =  imageOne.getRGB(0,0,width,height,imageArrayOne,0,width);

            //对第二张图片做相同的处理
            File  fileTwo  =  new  File(secondSrcImagePath);
            BufferedImage  imageTwo  =  ImageIO.read(fileTwo);
            int width2 = imageTwo.getWidth();
            int height2 = imageTwo.getHeight();
            int[]   ImageArrayTwo  =  new  int[width2*height2];
            ImageArrayTwo  =  imageTwo.getRGB(0,0,width,height,ImageArrayTwo,0,width);
            //ImageArrayTwo  =  imageTwo.getRGB(0,0,width2,height2,ImageArrayTwo,0,width2);

            //生成新图片
            //int width3 = (width>width2 || width==width2)?width:width2;
            BufferedImage  imageNew  =  new  BufferedImage(width,height*2,BufferedImage.TYPE_INT_RGB);
            //BufferedImage  imageNew  =  new  BufferedImage(width3,height+height2,BufferedImage.TYPE_INT_RGB);
            imageNew.setRGB(0,0,width,height,imageArrayOne,0,width);//设置上半部分的RGB
            imageNew.setRGB(0,height,width,height,ImageArrayTwo,0,width);//设置下半部分的RGB
            //imageNew.setRGB(0,height,width2,height2,ImageArrayTwo,0,width2);//设置下半部分的RGB

            File  outFile  =  new  File(toPath);
            ImageIO.write(imageNew,  imageFormat,  outFile);//写图片
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 纵向拼接一组（多张）图像
     * @param pics		将要拼接的图像数组
     * @param type	写入图像类型
     * @param dst_pic	写入图像路径
     * @return
     */
    public static boolean joinImageListVertical(String[] pics, String type, String dst_pic) {
        try {
            int len = pics.length;
            if (len < 1) {
                System.out.println("pics len < 1");
                return false;
            }
            File[] src = new File[len];
            BufferedImage[] images = new BufferedImage[len];
            int[][] imageArrays = new int[len][];
            for (int i = 0; i < len; i++) {
                //System.out.println(i);
                src[i] = new File(pics[i]);
                images[i] = ImageIO.read(src[i]);
                int width = images[i].getWidth();
                int height = images[i].getHeight();
                imageArrays[i] = new int[width * height];// 从图片中读取RGB
                imageArrays[i] = images[i].getRGB(0, 0, width, height,  imageArrays[i], 0, width);
            }

            int dst_height = 0;
            int dst_width = images[0].getWidth();
            for (int i = 0; i < images.length; i++) {
                dst_width = dst_width > images[i].getWidth() ? dst_width : images[i].getWidth();
                dst_height += images[i].getHeight();
            }
            //System.out.println(dst_width);
            //System.out.println(dst_height);
            if (dst_height < 1) {
                System.out.println("dst_height < 1");
                return false;
            }
            /*
             * 生成新图片
             */
            BufferedImage ImageNew = new BufferedImage(dst_width, dst_height,  BufferedImage.TYPE_INT_RGB);
            int height_i = 0;
            for (int i = 0; i < images.length; i++) {
                ImageNew.setRGB(0, height_i, dst_width, images[i].getHeight(),  imageArrays[i], 0, dst_width);
                height_i += images[i].getHeight();
            }
            File outFile = new File(dst_pic);
            ImageIO.write(ImageNew, type, outFile);// 写图片
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 将一组图片一次性附加合并到底图上
     * @param negativeImagePath		源图像（底图）路径
     * @param additionImageList	附加图像信息列表
     * @param imageFormat	图像写入格式
     * @param toPath	图像写入路径
     * @throws IOException
     */
    public static void mergeImageList(String negativeImagePath,List additionImageList,String imageFormat, String toPath) throws IOException{
        InputStream is= null;
        InputStream is2= null;
        OutputStream os = null;
        try{
            is=new FileInputStream(negativeImagePath);
            BufferedImage image=ImageIO.read(is);
            //Graphics g=image.getGraphics();
            Graphics2D g = image.createGraphics();;
            BufferedImage image2 = null;
            if(additionImageList != null){
                for(int i=0;i<additionImageList.size();i++){
                    //解析附加图片信息：x坐标、 y坐标、 additionImagePath附加图片路径
                    //图片信息存储在一个数组中
                    String[] additionImageInfo = (String[]) additionImageList.get(i);
                    int x = Integer.parseInt(additionImageInfo[0]);
                    int y = Integer.parseInt(additionImageInfo[1]);
                    String additionImagePath = additionImageInfo[2];
                    //读取文件输入流，并合并图片
                    is2 = new FileInputStream(additionImagePath);
                    //System.out.println(x+"  :  "+y+"  :  "+additionImagePath);
                    image2 = ImageIO.read(is2);
                    g.drawImage(image2,x,y,null);
                }
            }
            os = new FileOutputStream(toPath);
            ImageIO.write(image,  imageFormat,  os);//写图片
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            if(os != null){
                os.close();
            }
            if(is2 != null){
                is2.close();
            }
            if(is != null){
                is.close();
            }
        }
    }

    /**
     * 图片灰化操作
     * @param srcImage 读取图片路径
     * @param toPath	写入灰化后的图片路径
     * @param imageFormat 图片写入格式
     */
    public static void grayImage(String srcImage,String toPath,String imageFormat){
        try{
            BufferedImage src = ImageIO.read(new File(srcImage));
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
            ColorConvertOp op = new ColorConvertOp(cs, null);
            src = op.filter(src, null);
            ImageIO.write(src, imageFormat, new File(toPath));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 将附加图片合并到底图的指定坐标
     * @param backgroundImage 底图文件
     * @param additionImage	附加图片文件
     * @param x		起始的x坐标
     * @param y		起始的y坐标
     * @param mergeFile	合成图片文件
     */
    @SneakyThrows
    public static void mergeImageToOtherImage(File backgroundImage, File additionImage,
                                                   int x, int y, double ratio,
                                                   File mergeFile) {
        try{
            File tmpImage = additionImage;
            if (1d != ratio) {
                tmpImage = new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
                enlargementImageEqualProportion(additionImage.getAbsolutePath(), tmpImage.getAbsolutePath(), ratio);
            }

            BufferedImage image=ImageIO.read(backgroundImage);
            BufferedImage image2=ImageIO.read(tmpImage);

            Graphics2D g = image.createGraphics();
            g.drawImage(image2, x, y,null);

            //保存新图片
            ImageIO.write(image, mergeFile.getName().substring(mergeFile.getName().lastIndexOf(".") + 1), mergeFile);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 在源图片上设置水印文字
     * @param imageFile	源图片文件
     * @param fontPath	字体（例如：宋体）
     * @param fontStyle		字体格式(例如：普通样式--Font.PLAIN、粗体--Font.BOLD )
     * @param fontSize	字体大小
     * @param color	字体颜色(例如：黑色--Color.BLACK)
     * @param inputWords		输入显示在图片上的文字
     * @param x		文字显示起始的x坐标
     * @param y		文字显示起始的y坐标
     * @param xWidth    x坐标最大宽度
     * @param yHeight   y坐标最大高度
     * @param way   文字方向，0-横向， 1-纵向
     * @param toPath	写入图片路径
     * @throws IOException
     */
    @SneakyThrows
    public static void writeWordInImage(File imageFile,
                                        String fontPath, int fontStyle, int fontSize,
                                        Color color,
                                        String inputWords,
                                        int x, int y,
                                        int xWidth, int yHeight,
                                        int way,
                                        String toPath) {
        FileOutputStream fos=null;
        try {
            BufferedImage image = ImageIO.read(imageFile);
            //创建java2D对象
            Graphics2D g2d=image.createGraphics();
            // 抗锯齿 添加文字
            // VALUE_TEXT_ANTIALIAS_ON 改为 VALUE_TEXT_ANTIALIAS_LCD_HRGB
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.getDeviceConfiguration().createCompatibleImage(xWidth, yHeight, Transparency.TRANSLUCENT);
            //设置文字字体名称、样式、大小
            Font fontF = getFont(fontPath, fontStyle, fontSize);
            g2d.setFont(fontF);
            //设置字体颜色
            g2d.setColor(color);
            //按文字方向绘制文字
            if (way == 0) {
                // 横向换行算法
                drawLineWord(g2d, fontF, inputWords, x, y, xWidth);
            }else if (way == 1) {
                // 竖向换行算法
                drawVerticalWord(g2d, fontF, inputWords, x, y, yHeight);
            }
            g2d.dispose();
            fos=new FileOutputStream(toPath);
            ImageIO.write(image, imageFile.getName().substring(imageFile.getName().lastIndexOf(".") + 1), fos);
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            if(fos!=null){
                fos.close();
            }
        }
    }

    /**
     * @Author rym
     * @Description 横向写字换行算法
     * @Param []
     **/
    private static void drawLineWord(Graphics2D g2d, Font font, String words, int wordsX, int wordsY, int wordsWidth) {
        FontDesignMetrics metrics = FontDesignMetrics.getMetrics(font);
        // 获取字符的最高的高度
        int height = metrics.getHeight();

        int width = 0;
        int count = 0;
        int total = words.length();
        String subWords = words;
        int b = 0;
        for (int i = 0; i < total; i++) {
            // 统计字符串宽度 并与 预设好的宽度 作比较
            if (width <= wordsWidth) {
                // 获取每个字符的宽度
                width += metrics.charWidth(words.charAt(i));
                count++;
            } else {
                // 画 除了最后一行的前几行
                String substring = subWords.substring(0, count);
                g2d.drawString(substring, wordsX, wordsY + (b * height));
                subWords = subWords.substring(count);
                b++;
                width = 0;
                count = 0;
            }
            // 画 最后一行字符串
            if (i == total - 1) {
                g2d.drawString(subWords, wordsX, wordsY + (b * height));
            }
        }
    }

    /**
     * @Author rym
     * @Description 竖向写字换行方法
     * @Param []
     **/
    private static void drawVerticalWord(Graphics2D g2d, Font font, String words, int wordsX, int wordsY, int wordsHeight) {
        FontDesignMetrics metrics = FontDesignMetrics.getMetrics(font);
        // 获取字符的最高的高度
        int height = metrics.getHeight();
        // 获取换行宽度，为一个字加一个空格的宽度
        int width = metrics.charWidth('字') + metrics.charWidth(' ');

        int lastX = wordsX;
        int lastY = wordsY;

        //一个字一个字绘制
        for (int i = 0; i < words.length(); i ++) {
            if (lastY + height > wordsHeight) {
                lastY = wordsY;
                lastX = lastX + width;
            }
            g2d.drawString(words.charAt(i) + "", lastX, lastY);
            lastY += height;
        }
    }

    /**
     * 获取字体文件
     * @Author rym
     **/
    private static Font getFont(String fileName, int fontStyle, int fontSize) {
        File file = new File(FileUtils.getAbsolutePath(fileName));
        InputStream fi = null;
        BufferedInputStream fb = null;
        Font nf = null;

        try {
            // 字体文件
            fi = new FileInputStream(file);
            fb = new BufferedInputStream(fi);
            // 生成字体
            nf = Font.createFont(Font.TRUETYPE_FONT, fb);
            nf = nf.deriveFont(fontStyle, fontSize);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (FontFormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return nf;
    }
}


package com.redtourism.controller;

import com.redtourism.common.Constants;
import com.redtourism.common.NotLoginException;
import com.redtourism.common.Result;
import com.redtourism.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    /** 仅登录用户可上传，避免匿名写入服务器文件。先校验登录，再解析上传参数 */
    @PostMapping("/upload")
    public Result<String> upload(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(Constants.SESSION_USER) == null) {
            throw new NotLoginException("未登录或登录已过期，请先登录");
        }
        if (!(request instanceof MultipartHttpServletRequest)) {
            return Result.error("请选择要上传的文件");
        }
        MultipartFile file = ((MultipartHttpServletRequest) request).getFile("file");
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }
        String url = fileService.upload(file);
        return Result.success("上传成功", url);
    }
}

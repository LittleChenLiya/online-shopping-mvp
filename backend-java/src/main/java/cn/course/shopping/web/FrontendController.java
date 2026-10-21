package cn.course.shopping.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 仅开放四个前端资源，数据库、Java 源码及归档文件均不作为静态文件提供。 */
@RestController
public class FrontendController {
    @GetMapping({"/", "/index.html"}) public ResponseEntity<ClassPathResource> index() { return file("index.html", "text/html;charset=UTF-8"); }
    @GetMapping("/app.js") public ResponseEntity<ClassPathResource> app() { return file("app.js", "text/javascript;charset=UTF-8"); }
    @GetMapping("/api.js") public ResponseEntity<ClassPathResource> api() { return file("api.js", "text/javascript;charset=UTF-8"); }
    @GetMapping("/style.css") public ResponseEntity<ClassPathResource> style() { return file("style.css", "text/css;charset=UTF-8"); }
    private ResponseEntity<ClassPathResource> file(String name, String mime) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(new ClassPathResource("static/" + name));
    }
}

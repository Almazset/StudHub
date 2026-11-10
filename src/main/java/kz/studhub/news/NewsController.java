package kz.studhub.news;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

@Controller
public class NewsController {
    private final NewsRepository news;
    public NewsController(NewsRepository news) { this.news=news; }
    @ModelAttribute("categories") public NewsPost.Category[] categories() { return NewsPost.Category.values(); }
    @ModelAttribute("canPublishNews") public boolean canPublish(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    @GetMapping("/news") public String list(@RequestParam(required=false) NewsPost.Category category,
            @RequestParam(defaultValue="0") int page, Model model) {
        var paging=PageRequest.of(Math.max(0,page),12,Sort.by(Sort.Direction.DESC,"publishedAt","id"));
        model.addAttribute("newsPage",category == null ? news.findAll(paging) : news.findByCategory(category,paging));
        model.addAttribute("selectedCategory",category); return "news";
    }
    @GetMapping("/news/{id}") public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("post",find(id)); return "news-detail";
    }
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/news/new") public String create(Model model) {
        model.addAttribute("newsForm",new NewsForm()); model.addAttribute("postId",null); return "news-form";
    }
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/news/{id}/edit") public String edit(@PathVariable Long id,Model model) {
        model.addAttribute("newsForm",NewsForm.from(find(id))); model.addAttribute("postId",id); return "news-form";
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/news")
    public String publish(@Valid @ModelAttribute NewsForm newsForm, BindingResult errors,Model model) {
        if(errors.hasErrors()) { model.addAttribute("postId",null); return "news-form"; }
        NewsPost post=new NewsPost(); post.update(newsForm); return "redirect:/news/"+news.save(post).getId();
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/news/{id}")
    public String update(@PathVariable Long id,@Valid @ModelAttribute NewsForm newsForm,BindingResult errors,Model model) {
        NewsPost post=find(id);
        if(errors.hasErrors()) { model.addAttribute("postId",id); return "news-form"; }
        post.update(newsForm); news.save(post); return "redirect:/news/"+id;
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/news/{id}/delete") public String delete(@PathVariable Long id) {
        news.delete(find(id)); return "redirect:/news";
    }
    private NewsPost find(Long id) { return news.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
}

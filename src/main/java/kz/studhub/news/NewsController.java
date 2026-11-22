package kz.studhub.news;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NewsController {
    private static final int PAGE_SIZE = 12;
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
    private final NewsRepository news;

    public NewsController(NewsRepository news) {
        this.news = news;
    }

    @ModelAttribute("categories")
    public NewsPost.Category[] categories() {
        return NewsPost.Category.values();
    }

    @ModelAttribute("statuses")
    public NewsPost.Status[] statuses() {
        return NewsPost.Status.values();
    }

    @ModelAttribute("canPublishNews")
    public boolean canPublish(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/news")
    public String list(@RequestParam(required = false) NewsPost.Category category,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("newsPage", news.publishedPage(category, paging(page)));
        model.addAttribute("selectedCategory", category);
        return "news";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/news/manage")
    public String manage(@RequestParam(required = false) NewsPost.Category category,
                         @RequestParam(defaultValue = "") String state,
                         @RequestParam(name = "q", defaultValue = "") String query,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        Boolean published = switch (state) {
            case "" -> null;
            case "published" -> true;
            case "draft" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        String term = query.strip();
        if (term.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("newsPage", news.managePage(category, published, term, paging(page)));
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedState", state.isEmpty() ? null : state);
        model.addAttribute("searchQuery", term);
        return "news-manage";
    }

    @GetMapping("/news/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        NewsPost post = news.findById(id).orElseThrow(NewsController::notFound);
        if (!post.isPublished() && !canPublish(auth)) throw notFound();
        model.addAttribute("post", post);
        return "news-detail";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/news/new")
    public String create(Model model) {
        model.addAttribute("newsForm", new NewsForm());
        return formPage(model, null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/news/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        NewsPost post = news.findById(id).orElseThrow(NewsController::notFound);
        model.addAttribute("newsForm", NewsForm.from(post));
        return formPage(model, post);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/news")
    public String createPost(@Valid @ModelAttribute NewsForm newsForm, BindingResult errors,
                             @RequestParam(defaultValue = "publish") String intent, Model model) {
        boolean publish = switch (intent) {
            case "publish" -> true;
            case "draft" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        newsForm.validateDetails(errors);
        if (errors.hasErrors()) return formPage(model, null);
        NewsPost post = new NewsPost();
        post.update(newsForm);
        if (publish) post.publish();
        return "redirect:/news/" + news.save(post).getId();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/news/{id}")
    @Transactional
    public String update(@PathVariable Long id, @Valid @ModelAttribute NewsForm newsForm, BindingResult errors,
                         Model model) {
        NewsPost post = findLocked(id);
        newsForm.validateDetails(errors);
        if (errors.hasErrors()) return formPage(model, post);
        post.update(newsForm);
        return "redirect:/news/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/news/{id}/publish")
    @Transactional
    public String publish(@PathVariable Long id, RedirectAttributes redirect) {
        findLocked(id).publish();
        redirect.addFlashAttribute("newsNotice", "Новость опубликована.");
        return "redirect:/news/manage";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/news/{id}/unpublish")
    @Transactional
    public String unpublish(@PathVariable Long id, RedirectAttributes redirect) {
        findLocked(id).unpublish();
        redirect.addFlashAttribute("newsNotice", "Новость перемещена в черновики.");
        return "redirect:/news/manage";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/news/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, @RequestParam(required = false) String from,
                         RedirectAttributes redirect) {
        news.delete(findLocked(id));
        redirect.addFlashAttribute("newsNotice", "Новость удалена.");
        return "redirect:" + ("manage".equals(from) ? "/news/manage" : "/news");
    }

    private String formPage(Model model, NewsPost post) {
        model.addAttribute("postId", post == null ? null : post.getId());
        model.addAttribute("published", post != null && post.isPublished());
        return "news-form";
    }

    private static PageRequest paging(int page) {
        if (page > Integer.MAX_VALUE / PAGE_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return PageRequest.of(Math.max(0, page), PAGE_SIZE, NEWEST);
    }

    private NewsPost findLocked(Long id) {
        return news.findLocked(id).orElseThrow(NewsController::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}

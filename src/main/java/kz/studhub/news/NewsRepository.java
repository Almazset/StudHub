package kz.studhub.news;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface NewsRepository extends JpaRepository<NewsPost,Long> {
    Page<NewsPost> findByCategory(NewsPost.Category category, Pageable pageable);
    List<NewsPost> findTop3ByOrderByPublishedAtDescIdDesc();
}

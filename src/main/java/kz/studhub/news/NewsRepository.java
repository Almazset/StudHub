package kz.studhub.news;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRepository extends JpaRepository<NewsPost, Long> {
    @Query("""
        select post from NewsPost post
        where coalesce(post.published, true) = true
          and (:category is null or post.category = :category)
        """)
    Page<NewsPost> publishedPage(@Param("category") NewsPost.Category category, Pageable page);

    @Query("""
        select post from NewsPost post
        where coalesce(post.published, true) = true
        order by post.publishedAt desc, post.id desc
        """)
    List<NewsPost> latestPublished(Pageable page);

    @Query("""
        select post from NewsPost post
        where (:category is null or post.category = :category)
          and (:published is null or coalesce(post.published, true) = :published)
          and (:query = '' or locate(lower(:query), lower(post.title)) > 0)
        """)
    Page<NewsPost> managePage(@Param("category") NewsPost.Category category, @Param("published") Boolean published,
                              @Param("query") String query, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from NewsPost post where post.id = :id")
    Optional<NewsPost> findLocked(@Param("id") Long id);
}

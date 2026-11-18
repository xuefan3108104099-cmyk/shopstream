package com.shopstream.search.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import co.elastic.clients.json.JsonData;

import com.shopstream.search.domain.ProductDocument;
import com.shopstream.search.repository.ProductSearchRepository;

/**
 * GET /search combines four optional inputs: text on name/description, category,
 * a price range and paging. Text-only searches use the derived repository method;
 * every combination goes through ElasticsearchOperations.
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProductSearchRepository repository;
    private final ElasticsearchOperations operations;
    private final int defaultPageSize;

    public ProductSearchService(ProductSearchRepository repository, ElasticsearchOperations operations,
            @Value("${app.search.default-page-size:10}") int defaultPageSize) {
        this.repository = repository;
        this.operations = operations;
        this.defaultPageSize = defaultPageSize;
    }

    public SearchResult search(String text, String category, Double minPrice, Double maxPrice, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? defaultPageSize : Math.min(size, 100));
        boolean hasText = text != null && !text.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasPriceRange = minPrice != null || maxPrice != null;

        // simple case: only full-text, handled by a derived repository method
        if (hasText && !hasCategory && !hasPriceRange) {
            Page<ProductDocument> result = repository
                    .findByNameContainingOrDescriptionContaining(text, text, pageable);
            return new SearchResult(result.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize(),
                    result.getContent());
        }

        // combined case: one bool query, text in must, the rest as filters
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> {
                    if (hasText) {
                        bool.must(must -> must.multiMatch(multi -> multi.query(text).fields("name^3", "description")));
                    }
                    if (hasCategory) {
                        bool.filter(filter -> filter.term(term -> term.field("category").value(category)));
                    }
                    if (hasPriceRange) {
                        bool.filter(filter -> filter.range(range -> {
                            range.field("price");
                            if (minPrice != null) {
                                range.gte(JsonData.of(minPrice));
                            }
                            if (maxPrice != null) {
                                range.lte(JsonData.of(maxPrice));
                            }
                            return range;
                        }));
                    }
                    return bool;
                }))
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);
        List<ProductDocument> items = hits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .toList();
        log.debug("Search text='{}' category='{}' price=[{},{}] -> {} hits", text, category, minPrice, maxPrice,
                hits.getTotalHits());
        return new SearchResult(hits.getTotalHits(), pageable.getPageNumber(), pageable.getPageSize(), items);
    }

    public record SearchResult(long total, int page, int size, List<ProductDocument> items) {
    }
}

package com.shopstream.search.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopstream.search.service.ProductSearchService;
import com.shopstream.search.service.ProductSearchService.SearchResult;

/**
 * Exposed through the gateway as GET /api/search (StripPrefix removes /api).
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    private final ProductSearchService service;

    public SearchController(ProductSearchService service) {
        this.service = service;
    }

    @GetMapping
    public SearchResult search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.search(q, category, minPrice, maxPrice, page, size);
    }
}

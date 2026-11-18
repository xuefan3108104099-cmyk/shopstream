package com.shopstream.search.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.shopstream.search.domain.ProductDocument;

/**
 * Used for the simple "text only" search case and for the Kafka synchronisation.
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {

    Page<ProductDocument> findByNameContainingOrDescriptionContaining(String name, String description,
            Pageable pageable);
}

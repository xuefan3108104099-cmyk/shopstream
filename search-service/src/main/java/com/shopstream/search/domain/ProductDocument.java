package com.shopstream.search.domain;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Step 3: explicit mapping. name/description are full-text (text), category is a
 * keyword for exact filters, price is a double and stock an integer so both can
 * be used in range filters and sorting.
 */
@Document(indexName = "products")
public class ProductDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Double)
    private double price;

    @Field(type = FieldType.Integer)
    private int stock;

    public ProductDocument() {
    }

    public ProductDocument(Long id, String name, String description, String category, BigDecimal price, Integer stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price == null ? 0d : price.doubleValue();
        this.stock = stock == null ? 0 : stock;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}

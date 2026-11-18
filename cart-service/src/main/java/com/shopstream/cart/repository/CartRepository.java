package com.shopstream.cart.repository;

import org.springframework.data.repository.CrudRepository;

import com.shopstream.cart.domain.Cart;

public interface CartRepository extends CrudRepository<Cart, String> {
}

package com.gateway.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllProducts_returnsProductListWithServiceName() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("product-service"))
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    void getProductById_returnsThatSpecificProduct() throws Exception {
        mockMvc.perform(get("/products/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101));
    }
}
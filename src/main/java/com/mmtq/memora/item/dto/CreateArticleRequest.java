package com.mmtq.memora.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateArticleRequest(
    @NotBlank( message = "Title is required for articles" )
    @Size( max = 500, message = "Title cannot exceed 500 characters" )
    String title,

    @NotBlank( message = "Description is required for articles" )
    String description
) {}

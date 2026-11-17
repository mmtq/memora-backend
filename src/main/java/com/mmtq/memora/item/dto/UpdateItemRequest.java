package com.mmtq.memora.item.dto;

import jakarta.validation.constraints.Size;

public record UpdateItemRequest(
    @Size( max = 500, message = "Title cannot exceed 500 characters" )
    String title,

    String description
) {}

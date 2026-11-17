package com.mmtq.memora.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
    @Size( max = 500, message = "Title cannot exceed 500 characters" )
    String title,

    @NotBlank( message = "Description is required for notes" )
    String description
) {}

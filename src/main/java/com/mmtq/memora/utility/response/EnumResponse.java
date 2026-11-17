package com.mmtq.memora.utility.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnumResponse {
    private String name;
    private String constant;

    // --- Static factory helper ---

    public static EnumResponse of( Enum<?> enumValue ) {
        return new EnumResponse( enumValue.name(), enumValue.toString() );
    }
}

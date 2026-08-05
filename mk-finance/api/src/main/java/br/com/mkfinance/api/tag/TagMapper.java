package br.com.mkfinance.api.tag;

import br.com.mkfinance.domain.model.Tag;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TagMapper {

    public TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }

    public List<TagResponse> toResponseList(List<Tag> tags) {
        return tags.stream().map(this::toResponse).toList();
    }
}

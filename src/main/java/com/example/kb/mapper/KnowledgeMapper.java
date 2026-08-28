package com.example.kb.mapper;
import com.example.kb.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface KnowledgeMapper {

    void insert(Knowledge knowledge);

    void batchInsert(List<Knowledge> list);

    Knowledge findById(Long id);

    List<Knowledge> findAll();

    void update(Knowledge knowledge);

    void deleteById(Long id);
}

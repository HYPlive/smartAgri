package com.example.smartAgr.service.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeInitService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeInitService.class);

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourceResolver;

    public KnowledgeInitService(VectorStore vectorStore, ResourcePatternResolver resourceResolver) {
        this.vectorStore = vectorStore;
        this.resourceResolver = resourceResolver;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("=== 开始加载知识库文档 ===");
            List<Document> allDocs = new ArrayList<>();

            Resource[] resources = resourceResolver.getResources("classpath:/static/docs/*.{md,txt}");
            if (resources.length == 0) {
                log.warn("未在 static/docs/ 目录下找到知识文档 (.md/.txt)");
                return;
            }

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("  读取文档: {}", filename);
                try {
                    TextReader reader = new TextReader(resource);
                    List<Document> docs = reader.get();
                    // 添加文件名到元数据，方便追溯来源
                    docs.forEach(d -> d.getMetadata().put("source", filename));
                    allDocs.addAll(docs);
                } catch (Exception e) {
                    log.error("  读取文档失败: {} - {}", filename, e.getMessage());
                }
            }

            // 分块：每500字一块，50字重叠避免切断上下文
            TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 10, 5000, false);
            List<Document> chunks = splitter.apply(allDocs);
            log.info("  文档分块完成: {} 个原始文档 → {} 个文本块", allDocs.size(), chunks.size());

            // 向量化并存入 VectorStore
            vectorStore.add(chunks);
            log.info("=== 知识库加载完成: {} 个文本块已入库 ===", chunks.size());

        } catch (Exception e) {
            log.error("知识库初始化失败: {}", e.getMessage(), e);
        }
    }
}

package com.byesfi.aitestlab;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.retriever.VectorStoreRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class AiTestLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiTestLabApplication.class, args);
	}

	@Bean
	VectorStore vectorStore (EmbeddingClient ec, JdbcTemplate t){
		return new PgVectorStore(t, ec);
	}
	@Bean
	VectorStoreRetriever vectorStoreRetriever(VectorStore vs){
		return new VectorStoreRetriever(vs, 4, 0.75);
	}
	@Bean
	TokenTextSplitter tokenTextSplitter(){
		return new TokenTextSplitter();
	}

	static void init(VectorStore vectorStore, JdbcTemplate template, Resource pdfResource) throws Exception{

		//cleaning the vector store
		template.execute("delete from vector_store");

		//Reading the document
		var config = PdfDocumentReaderConfig.builder()
				.withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3).build())
				.withPagesPerDocument(1)
				.build();
		var pdfReader = new PagePdfDocumentReader(pdfResource, config);

		//TokenTextSplitter used to define how are define in the pdf document
		var textSplitter = new TokenTextSplitter();
		List<Document> documentTokenList = textSplitter.apply(pdfReader.get());

		// Writing the document token list in the VectorStore
		// and that will be used in the context to create the range
		vectorStore.accept(documentTokenList);
	}

	@Bean
	ApplicationRunner applicationRunner(Chatbot chatbot, VectorStore vectorStore, JdbcTemplate jdbcTemplate,
										@Value("file:///${HOME}/Projects/aitestlab/pdf/kubernetes-FAQ.pdf") Resource resource){
		return args -> {
			init(vectorStore, jdbcTemplate, resource);

			var message = chatbot.chat("Who are you?");
			System.out.println("response: " + message);
        };
	}


	@Component
	class Chatbot{

		//TODO: Fix the template to answer just question on context of document..
		private final String template = """
			Your main responsibility is to answer questions related to Kubernetes services, 
			utilizing information from the DOCUMENTS section for accuracy. 
			If a question is outside the Kubernetes context or not covered in the documents, 
			please explicitly state that you don't have the information. 
			The DOCUMENTS section serves as the sole source for precise and relevant responses.

			DOCUMENTS:
			{documents}
			
			thank you.
			""";
		private final ChatClient chatClient;

		private final VectorStoreRetriever vectorStoreRetriever;

		public Chatbot(ChatClient chatClient, VectorStoreRetriever vectorStoreRetriever) {
			this.chatClient = chatClient;
			this.vectorStoreRetriever = vectorStoreRetriever;
		}

		public String chat(String message){

			var listOfSimilarDocuments = this.vectorStoreRetriever.retrieve(message);
			var documents = listOfSimilarDocuments
					.stream()
					.map(Document::getContent)
					.collect(Collectors.joining(System.lineSeparator()));
			var systemMessage = new SystemPromptTemplate(this.template)
					.createMessage(Map.of("documents", documents));
			var userMessage = new UserMessage(message);
			var prompt = new Prompt(List.of(systemMessage, userMessage));
			var aiResponse = chatClient.generate(prompt);
			return aiResponse.getGeneration().getContent();
		}
	}

}

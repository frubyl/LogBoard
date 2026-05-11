package com.github.logboard.log.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "logs")
data class LogDocumentEs(
    @Id val id: String,
    @Field(type = FieldType.Keyword) val projectId: String,
    @Field(type = FieldType.Keyword) val ingestionId: String,
    @Field(type = FieldType.Keyword) val level: String,
    @Field(type = FieldType.Text) val message: String,
    @Field(type = FieldType.Long) val timestamp: Long
)

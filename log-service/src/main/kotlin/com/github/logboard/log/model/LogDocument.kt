package com.github.logboard.log.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "logs", createIndex = true)
data class LogDocument(
    @Id val id: String,
    @Field(name = "project_id", type = FieldType.Keyword) val projectId: String,
    @Field(name = "ingestion_id", type = FieldType.Keyword) val ingestionId: String,
    @Field(type = FieldType.Keyword) val level: String,
    @Field(type = FieldType.Text) val message: String,
    @Field(type = FieldType.Date, format = [DateFormat.epoch_millis]) val timestamp: Long
)

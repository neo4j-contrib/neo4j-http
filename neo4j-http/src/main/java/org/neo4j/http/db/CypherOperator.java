/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.http.db;

/**
 * From <a href="https://neo4j.com/docs/cypher-manual/current/execution-plans/operator-summary/">Neo4j Operator Summary v4.4</a>
 *
 * @author Michael J. Simons
 */
enum CypherOperator {
	/**
	 * Fallback for all the cases in which an unknown operator is encountered. We do assume updates.
	 */
	__UNKNOWN__(true),
	AllNodesScan,
	Anti,
	AntiSemiApply,
	Apply,
	Argument,
	AssertSameNode,
	AssertingMultiNodeIndexSeek,
	CacheProperties,
	CartesianProduct,
	Create(true),
	CreateIndex(true),
	CreateNodeKeyConstraint(true),
	CreateNodePropertyExistenceConstraint(true),
	CreateRelationshipPropertyExistenceConstraint(true),
	CreateUniqueConstraint(true),
	Delete(true),
	DetachDelete(true),
	DirectedRelationshipByIdSeek,
	DirectedRelationshipIndexContainsScan,
	DirectedRelationshipIndexEndsWithScan,
	DirectedRelationshipIndexScan,
	DirectedRelationshipIndexSeek,
	DirectedRelationshipIndexSeekByRange,
	DirectedRelationshipTypeScan,
	Distinct,
	DoNothingIfExists,
	DropConstraint(true),
	DropIndex(true),
	DropNodeKeyConstraint(true),
	DropNodePropertyExistenceConstraint(true),
	DropRelationshipPropertyExistenceConstraint(true),
	DropUniqueConstraint(true),
	Eager,
	EagerAggregation,
	EmptyResult,
	EmptyRow,
	ExhaustiveLimit,
	Expand,
	Filter,
	Foreach,
	LetAntiSemiApply,
	LetSelectOrAntiSemiApply,
	LetSelectOrSemiApply,
	LetSemiApply,
	Limit,
	LoadCSV,
	LockingMerge(true),
	Merge(true),
	MultiNodeIndexSeek,
	NodeByIdSeek,
	NodeByLabelScan,
	NodeCountFromCountStore,
	NodeHashJoin,
	NodeIndexContainsScan,
	NodeIndexEndsWithScan,
	NodeIndexScan,
	NodeIndexSeek,
	NodeIndexSeekByRange,
	NodeLeftOuterHashJoin,
	NodeRightOuterHashJoin,
	NodeUniqueIndexSeek,
	NodeUniqueIndexSeekByRange,
	Optional,
	OptionalExpand,
	OrderedAggregation,
	OrderedDistinct,
	PartialSort,
	PartialTop,
	ProcedureCall(true),
	ProduceResults,
	ProjectEndpoints,
	Projection,
	RelationshipCountFromCountStore,
	RemoveLabels(true),
	RollUpApply,
	SelectOrAntiSemiApply,
	SelectOrSemiApply,
	SemiApply,
	SetLabels(true),
	SetNodePropertiesFromMap(true),
	SetProperty(true),
	SetRelationshipPropertiesFromMap(true),
	ShortestPath,
	ShowConstraints,
	ShowFunctions,
	ShowIndexes,
	ShowProcedures,
	ShowTransactions,
	Skip,
	Sort,
	TerminateTransactions,
	Top,
	TriadicBuild,
	TriadicFilter,
	TriadicSelection,
	UndirectedRelationshipByIdSeek,
	UndirectedRelationshipIndexContainsScan,
	UndirectedRelationshipIndexEndsWithScan,
	UndirectedRelationshipIndexScan,
	UndirectedRelationshipIndexSeek,
	UndirectedRelationshipIndexSeekByRange,
	UndirectedRelationshipTypeScan,
	Union,
	Unwind,
	ValueHashJoin,
	VarLengthExpand;

	private final boolean updating;

	CypherOperator() {
		this(false);
	}

	CypherOperator(boolean updating) {
		this.updating = updating;
	}

	public boolean isUpdating() {
		return updating;
	}
}

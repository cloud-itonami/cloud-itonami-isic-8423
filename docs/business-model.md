# Business Model

The public safety administrative actor enables public safety agencies to operate their administrative back-office autonomously, with full audit trails and human-in-the-loop checkpoints.

**Core capabilities:**
- Independent incident intake and logging (no operational authority)
- Public records request processing (no sensitive/ongoing-case disclosure)
- Resource scheduling for administrative purposes (no tactical deployment)
- Statistical anomaly flagging (escalates to human analyst)

**Value proposition:**
- Transparency: full append-only audit ledger visible to the agency
- Independence: self-hosted, no closed SaaS vendor lock
- Accountability: every action checkpointed and logged

**Economic model:**
- OSS, AGPL-3.0-or-later
- Reference implementation (langgraph-clj StateGraph) provided
- Organizations fork and customize governance policies (escalation thresholds, approval workflows)
- Agencies retain their data and audit trail

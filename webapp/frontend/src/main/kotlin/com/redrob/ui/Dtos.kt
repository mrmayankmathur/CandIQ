package com.redrob.ui

import kotlinx.serialization.Serializable

// ── Job description ──────────────────────────────────────────────────────────

@Serializable
data class SkillGroup(
    val category: String = "",
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val weight: Double = 0.0,
)

@Serializable
data class ExperienceBand(
    val statedMin: Int = 0,
    val statedMax: Int = 0,
    val idealMin: Int = 0,
    val idealMax: Int = 0,
    val note: String? = null,
)

@Serializable
data class JdLocation(
    val preferred: List<String> = emptyList(),
    val acceptable: List<String> = emptyList(),
    val workMode: String? = null,
    val noticePeriodIdealDays: Int = 0,
)

@Serializable
data class Disqualifier(val id: String = "", val description: String = "", val severity: String = "")

@Serializable
data class JobDescription(
    val jobTitle: String = "",
    val company: String = "",
    val summary: String = "",
    val experience: ExperienceBand = ExperienceBand(),
    val location: JdLocation = JdLocation(),
    val mustHave: List<SkillGroup> = emptyList(),
    val niceToHave: List<SkillGroup> = emptyList(),
    val disqualifiers: List<Disqualifier> = emptyList(),
    val coreDomainTerms: List<String> = emptyList(),
)

// ── Ranking + profile ────────────────────────────────────────────────────────

@Serializable
data class RankedResult(
    val rank: Int = 0,
    val candidateId: String = "",
    val score: Double = 0.0,
    val reasoning: String? = null,
)

@Serializable
data class ProfileSummary(
    val candidateId: String = "",
    val name: String? = null,
    val headline: String? = null,
    val currentTitle: String? = null,
    val currentCompany: String? = null,
    val currentIndustry: String? = null,
    val yearsOfExperience: Double = 0.0,
    val location: String? = null,
    val country: String? = null,
    val topSkills: List<String> = emptyList(),
    val openToWork: Boolean = false,
    val noticePeriodDays: Int = 0,
    val recruiterResponseRate: Double = 0.0,
    val lastActiveDate: String? = null,
)

@Serializable
data class RankedCandidate(val ranking: RankedResult = RankedResult(), val summary: ProfileSummary? = null)

@Serializable
data class Profile(
    val anonymizedName: String? = null,
    val headline: String? = null,
    val summary: String? = null,
    val location: String? = null,
    val country: String? = null,
    val yearsOfExperience: Double = 0.0,
    val currentTitle: String? = null,
    val currentCompany: String? = null,
    val currentCompanySize: String? = null,
    val currentIndustry: String? = null,
)

@Serializable
data class Job(
    val company: String? = null,
    val title: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val durationMonths: Int = 0,
    val current: Boolean = false,
    val industry: String? = null,
    val companySize: String? = null,
    val description: String? = null,
)

@Serializable
data class Education(
    val institution: String? = null,
    val degree: String? = null,
    val fieldOfStudy: String? = null,
    val startYear: Int = 0,
    val endYear: Int = 0,
    val grade: String? = null,
    val tier: String? = null,
)

@Serializable
data class Skill(
    val name: String = "",
    val proficiency: String? = null,
    val endorsements: Int = 0,
    val durationMonths: Int? = null,
)

@Serializable
data class Certification(val name: String = "", val issuer: String? = null, val year: Int = 0)

@Serializable
data class Language(val language: String = "", val proficiency: String? = null)

@Serializable
data class SalaryRange(val min: Double = 0.0, val max: Double = 0.0)

@Serializable
data class Signals(
    val profileCompletenessScore: Double = 0.0,
    val lastActiveDate: String? = null,
    val openToWork: Boolean = false,
    val recruiterResponseRate: Double = 0.0,
    val noticePeriodDays: Int = 0,
    val expectedSalaryLpa: SalaryRange = SalaryRange(),
    val preferredWorkMode: String? = null,
    val willingToRelocate: Boolean = false,
    val githubActivityScore: Double = 0.0,
    val profileViewsReceived30d: Int = 0,
    val savedByRecruiters30d: Int = 0,
    val connectionCount: Int = 0,
    val endorsementsReceived: Int = 0,
    val interviewCompletionRate: Double = 0.0,
    val offerAcceptanceRate: Double = 0.0,
    val verifiedEmail: Boolean = false,
    val verifiedPhone: Boolean = false,
    val linkedinConnected: Boolean = false,
)

@Serializable
data class CandidateProfile(
    val candidateId: String = "",
    val profile: Profile = Profile(),
    val careerHistory: List<Job> = emptyList(),
    val education: List<Education> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val certifications: List<Certification> = emptyList(),
    val languages: List<Language> = emptyList(),
    val signals: Signals = Signals(),
)

@Serializable
data class MatchFactor(val label: String = "", val value: Double = 0.0, val detail: String? = null)

@Serializable
data class MatchBreakdown(
    val overall: Double = 0.0,
    val factors: List<MatchFactor> = emptyList(),
    val matchedSkills: List<String> = emptyList(),
    val note: String? = null,
)

@Serializable
data class CandidateDetail(
    val ranking: RankedResult = RankedResult(),
    val profile: CandidateProfile = CandidateProfile(),
    val match: MatchBreakdown = MatchBreakdown(),
)

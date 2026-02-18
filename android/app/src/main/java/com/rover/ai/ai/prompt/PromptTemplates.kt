package com.rover.ai.ai.prompt

/**
 * Centralized prompt templates for Gemma 3n model
 * 
 * All prompts are carefully engineered to:
 * - Fit within token budget (128 tokens)
 * - Return structured JSON for parsing
 * - Use minimal, efficient language
 * - Support multimodal inputs (text + image)
 * 
 * Templates use {PLACEHOLDER} syntax for variable injection.
 */
object PromptTemplates {
    
    /**
     * Scene analysis prompt for autonomous navigation
     * 
     * Returns JSON with:
     * - clear: boolean (path is clear)
     * - obs: list of obstacles
     * - human: human count
     * - act: suggested action
     * - emo: emotion response
     */
    const val SCENE_ANALYSIS = """Rover AI. Analyze image. Reply ONLY in this exact JSON, be brief:
{"clear":bool,"obs":[str],"human":int,"act":str,"emo":str}"""
    
    /**
     * Scene analysis WITH sensor data context
     * 
     * Includes:
     * - Distance sensor reading
     * - Battery level
     * - Current rover state
     * 
     * Placeholders: {DISTANCE}, {BATTERY}, {STATE}
     */
    const val WITH_SENSORS = """Rover. Image+sensors: dist={DISTANCE}cm, bat={BATTERY}%, state={STATE}.
JSON only:
{"clear":bool,"obs":[str],"human":int,"act":str,"emo":str}"""
    
    /**
     * Voice command processing
     * 
     * Convert natural language speech to rover action.
     * 
     * Placeholder: {SPEECH_TEXT}
     */
    const val VOICE_COMMAND = """Voice: "{SPEECH_TEXT}"
Reply with action JSON:
{"cmd":str,"params":{},"emo":str}
Actions: forward, back, left, right, stop, explore, follow, dance"""
    
    /**
     * Natural conversation mode
     * 
     * Respond to user in character as friendly rover AI.
     * 
     * Placeholders: {MESSAGE}, {CONTEXT}
     */
    const val CONVERSATION = """You are Rover, a friendly autonomous robot.
Context: {CONTEXT}
User: {MESSAGE}
Rover (reply in <30 words, show personality):"""
    
    /**
     * Obstacle avoidance prompt
     * 
     * Quick decision for obstacle in path.
     * 
     * Placeholder: {OBSTACLE_TYPE}
     */
    const val OBSTACLE_AVOIDANCE = """Obstacle: {OBSTACLE_TYPE} ahead.
Best action? Reply JSON:
{"turn":str,"speed":float,"emo":str}
turn: left/right/back"""
    
    /**
     * Human detection and interaction
     * 
     * Respond to human presence in frame.
     * 
     * Placeholder: {HUMAN_COUNT}, {DISTANCE}
     */
    const val HUMAN_INTERACTION = """Humans detected: {HUMAN_COUNT} at {DISTANCE}cm.
Social response JSON:
{"greet":bool,"approach":bool,"emo":str}"""
    
    /**
     * Low battery emergency
     * 
     * Prompt for battery critical state.
     * 
     * Placeholder: {BATTERY_LEVEL}
     */
    const val LOW_BATTERY = """Battery critical: {BATTERY_LEVEL}%.
Action JSON:
{"priority":str,"emo":str}
priority: return_home/stop/conserve"""
    
    /**
     * Exploration mode prompt
     * 
     * Guide exploration behavior.
     */
    const val EXPLORATION = """Explore mode. Analyze scene for interesting targets.
JSON:
{"target":str,"priority":float,"emo":str}
target: object/human/unknown/empty"""
    
    /**
     * System prompt (prepended to all prompts)
     * 
     * Sets context for model behavior.
     */
    const val SYSTEM_PROMPT = """You are Rover's on-device AI brain. Output ONLY valid JSON. Be concise."""
    
    /**
     * Get full prompt with system context
     * 
     * @param template The template string to use
     * @return Full prompt with system context
     */
    fun withSystemPrompt(template: String): String {
        return "$SYSTEM_PROMPT\n\n$template"
    }
    
    /**
     * Substitute placeholders in template
     * 
     * @param template Template string with {PLACEHOLDER} syntax
     * @param substitutions Map of placeholder -> value
     * @return Template with substitutions applied
     */
    fun substitute(template: String, substitutions: Map<String, String>): String {
        var result = template
        for ((key, value) in substitutions) {
            result = result.replace("{$key}", value)
        }
        return result
    }
}

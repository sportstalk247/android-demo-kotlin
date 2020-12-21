# sdk-android-kotlin

## Implementing the SDK
You can download the latest SportsTalk Android SDK from the following location:
https://gitlab.com/sportstalk247/sdk-android-kotlin
You need to register SportsTalk API with 'Appkey' and 'Token'.
How to get API Key and Token
You need to visit the dashboard with the following URL:
https://dashboard.sportstalk247.com
Then click on ''Application Management'' link to generate the above

## How to download the SDK from public repository
The SportsTalk SDK has been published into **jitpack.io**.
In order to use it in your application, just do the following:

### Add the following in root  **build.gradle** file
```groovy
allprojects {
   repositories {
      // ...
      maven {
         url "https://jitpack.io"
      }
   }
}
```

### Then, under **local.properties** file, provide the following property entries:
```properties
sportstalk247.urlEndpoint=<Your PROD URL Endpoint>
sportstalk247.authToken=<Your PROD Auth Token generated from above>
sportstalk247.appid=<Your PROD App ID generated from above>

sportstalk247.qa.urlEndpoint=<Your QA URL Endpoint>
sportstalk247.qa.authToken=<Your QA Auth Token generated from above>
sportstalk247.qa.appid=<Your QA App ID generated from above>
```
NOTE: Make sure the url end point must have a trailing '/' character.

### Add the following lines in your module **build.gradle** file:
Before `android {}` declaration
```groovy
def localProperties = new Properties()
localProperties.load(new FileInputStream(rootProject.file("local.properties")))
```
From your `android.buildTypes`
```groovy
buildTypes {
    debug {
        // ...
        resValue "string", "sportstalk247_urlEndpoint", localProperties.getProperty("sportstalk247.qa.urlEndpoint", "")
        resValue "string", "sportstalk247_authToken", localProperties.getProperty("sportstalk247.qa.authToken", "")
        resValue "string", "sportstalk247_appid", localProperties.getProperty("sportstalk247.qa.appid", "")
    }
    release {
        // ...
        resValue "string", "sportstalk247_urlEndpoint", localProperties.getProperty("sportstalk247.urlEndpoint", "")
        resValue "string", "sportstalk247_authToken", localProperties.getProperty("sportstalk247.authToken", "")
        resValue "string", "sportstalk247_appid", localProperties.getProperty("sportstalk247.appid", "")
    }
}
```
NOTE: Above can also be applied under `android.productFlavors`, depending on client project build setup

### Under dependencies section
```groovy
implementation 'com.gitlab.sportstalk247:sdk-android-kotlin:vX.Y.Z'
```
[![Release](https://jitpack.io/v/com.gitlab.sportstalk247/sdk-android-kotlin.svg)](https://jitpack.io/#com.gitlab.sportstalk247/sdk-android-kotlin)

Then sync again. The gradle build should now be successful.
    
# How to Use
## Instantiate SportsTalkManager Client
This Sportstalk SDK is meant to power custom chat applications. Sportstalk does not enforce any restricitons on your UI design, but instead empowers your developers to focus on the user experience without worrying about the underlying chat behavior.    
    
Android Sportstalk SDK is an Asynchronous-driven API, powered by Kotlin [Coroutines]([https://developer.android.com/kotlin/coroutines](https://developer.android.com/kotlin/coroutines)) to gracefully handle asynchronous operations.

```kotlin
class MyFragment: Fragment() {  
  
    //...
    //...
   val appId = "c84cb9c852932a6b0411e75e"    
   val apiToken = "5MGq3XbsspBEQf3kj154_OSQV-jygEKwHJyuHjuAeWHA"     
   val endpoint = "http://api.custom.endpoint/v1/" // please ensure out of the box the SDKs are configured for production URL  
       
   val userClient = SportsTalk247.UserClient(    
      config = ClientConfig(    
         appId = appId,     
         apiToken = apiToken,    
         endpoint = endpoint    
      )    
   )    
   // Launch thru coroutine block    
   // https://developer.android.com/topic/libraries/architecture/coroutines    
   lifecycleScope.launch {    
      // Switch to IO Coroutine Context(Operation will be executed on IO Thread)    
      val createdUser = withContext(Dispatchers.IO) {    
         userClient.createOrUpdateUser(    
            request = CreateUpdateUserRequest(    
                        userid = "<USERID>",    
                        handle = "sample_handle_123",    
                        displayname = "Test Name 123", // OPTIONAL    
                        pictureurl = "<Image URL>", // OPTIONAL    
                        profileurl = "<Image URL>" // OPTIONAL    
                    )   
         )   
      }
          
      // Resolve `createdUser` from HERE onwards(ex. update UI displaying the response data)...
   }
}  
```

   
## How to use Chat Client
```kotlin  
// Under Fragment class  
val chatClient = SportsTalk247.ChatClient(    
   config = ClientConfig(    
      appId = "c84cb9c852932a6b0411e75e",    
      apiToken = "5MGq3XbsspBEQf3kj154_OSQV-jygEKwHJyuHjuAeWHA",    
      endpoint = "http://api.custom.endpoint/v1/"    
   )    
)    
```  
### Join Chat Room
```kotlin  
// Under Fragment class  
lifecycleScope.launch {    
   // Assuming that there is already an existing User instance    
   val testUser = User(...)    
   // Assuming that there is already an existing ChatRoom instance    
   val testChatRoom = ChatRoom(..., customid = "new-york-room-1", ...)    
   // Attempt Join Chat Room    
   val joinResponse = withContext(Dispatchers.IO) {    
      chatApi.joinRoomByCustomId(    
         chatRoomCustomId = testChatRoom.customid!!,    
         request = JoinChatRoomRequest(    
            userid = testUser.userid!!,    
            handle = testUser.handle!!    
         )    
      )
   }
   
   // Once joined, the developer may immediately access the join response's `eventscursor.events` field, which contains an initial list of messages of the chat room
   val initialMessages: List<ChatEvent> = joinResponse.eventscursor?.events ?: listOf()
   // ex. display in UI the initial chat messages/events
}  
```
### Listen to Event Updates

The Chat service provides a polling mechanism to dispatch chat event/message updates every N milliseconds(provided as frequency argument). Chat event/message updates are dispatched in a reactive pattern. The SDK exposes 3 Reactive framework types for the developer to use in order implement this feature, namely as follows:

* [Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html)
Below are the following dependencies in order to use this framework:
```gradle
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.1"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.3.1"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.1"
```

* [Rx2Java](https://github.com/Kotlin/kotlinx.coroutines/blob/master/reactive/kotlinx-coroutines-rx2/README.md)
Below are the following dependencies in order to use this framework:
```gradle
implementation "io.reactivex.rxjava2:rxandroid:2.1.1"
implementation "io.reactivex.rxjava2:rxkotlin:2.4.0"
```

* [LiveData](https://developer.android.com/kotlin/ktx\#livedata) extensions
Below are the following dependencies in order to use this framework:
```gradle
implementation "androidx.lifecycle:lifecycle-extensions:2.2.0"
kapt "androidx.lifecycle:lifecycle-common-java8:2.2.0"
implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.2.0"
implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.2.0"
```

This gives enough flexibility to any developers whichever framework they are familiar with.


```kotlin  
import com.sportstalk.api.polling.coroutines.allEventUpdates    
  
// Under Fragment class  
lifecycleScope.launch {  
   // Now that the test user has joined the room, setup reactive subscription to event updates  
   // Below returns a Flow<List<ChatEvent>>    
   chatClient.allEventUpdates(      
     chatRoomId = testChatRoom.id!!,      
     lifecycleOwner = viewLifecycleOwner /* Already provided by androidx.Fragment */,    
     frequency = 1000L /* Polling Frequency. Defaults to 500 milliseconds if not explicitly provided */,    
     /*      
     * The following are placeholder/convenience functions should the developers want to implement it     
     * in a callback-oriented way. (Invoked as subscription's side-effect. In RxJava, these are invoked via .doOnNext { ... }. In coroutine flow, these are invoked via .onEach { ... })    
     */    
     onChatEvent = { event: ChatEvent -> /* Handle all other eventtype */ }, // OPTIONAL    
     onGoalEvent = { goalEvent: ChatEvent -> /* Handle eventtype == "goal" */ }, // OPTIONAL    
     onAdEvent = { adEvent: ChatEvent -> /* Handle eventtype == "advertisement" */ }, // OPTIONAL    
     onReply = { replyEvent: ChatEvent -> /* Handle eventtype == "reply" */ }, // OPTIONAL    
     onReaction = { reactionEvent: ChatEvent -> /* Handle eventtype == "reaction" */ }, // OPTIONAL    
     onPurgeEvent = { purgeEvent: ChatEvent -> /* Handle eventtype == "purge" */ } // OPTIONAL    
   )    
   .distinctUntilChanged()    
   .onEach { events ->    
      // Alternatively, the developer can opt to consume the events in here...    
      // NOTE:: ONLY choose 1 approach to avoid handling it twice.    
          
      // Iterate each event item(s)    
      events.forEach { chatEvent ->    
         when(chatEvent.eventtype) {    
            EventType.GOAL -> { /* Handle goal event types */ }    
            EventType.ADVERTISEMENT -> { /* Handle advertisements event types */ }    
            ...    
            ...    
         }    
      }    
    
   }    
   .launchIn(lifecycleScope /* Already provided by androidx.Fragment */)    
       
   // Then, perform start listening to event updates    
   chatClient.startListeningToChatUpdates(forRoomId = testChatRoom.id!!)    
       
   // At some point in time, the developer might want to explicitly stop listening to event updates    
   chatClient.stopListeningToChatUpdates(forRoomId = testChatRoom.id!!)    
```  
### Send a Chat Message
```kotlin  
   // To Send a Chat Message    
   val executeChatResponse = withContext(Dispatchers.IO) {    
      chatApi.executeChatCommand(    
         chatRoomId = testChatRoom.id!!,    
         request = ExecuteChatCommandRequest(      
           command = "Yow Jessy, how are you doin'?",      
           userid = testUser.userid!!     
         )    
      )
   }    
   // Resolve `executeChatResponse` (ex. Display prompt OR Update UI)    
}  
```    
### Set Message/Event as Deleted
```kotlin  
// Execute within coroutine scope  
lifecycleScope.launch {  
   // This is with the assumption that   
   // 1. A test user has been created(or already exists)  
   // 2. A test chat room has already been created(or already exists)  
   // 3. Test user already joined the room  
   // 4. There exists a message/event on that room  
   val removeMessageResponse = withContext(Dispatchers.IO) {  
      chatClient.setMessageAsDeleted(
                  chatRoomId = testChatRoom.id!!,    
                  eventId = testEvent.id!!,  
                  userid = testUser.userid!!,
                  deleted = false,
                  permanentifnoreplies = false // OPTIONAL
      )
   }
   
   // Resolve `removeMessageResponse`, ex. update UI or display a prompt
}  
```  
  
## How to use Comment Client
```kotlin  
// Under Fragment class  
val commentClient = SportsTalk247.CommentClient(
   config = ClientConfig(    
      appId = "c84cb9c852932a6b0411e75e",    
      apiToken = "5MGq3XbsspBEQf3kj154_OSQV-jygEKwHJyuHjuAeWHA",    
      endpoint = "http://api.custom.endpoint/v1/"    
   )    
)    
```  
### Create a Conversation
```kotlin  
// Under Fragment class  
// Execute within coroutine scope  
lifecycleScope.launch {  
   val createConversationResponse = withContext(Dispatchers.IO) {  
      commentClient.createOrUpdateConversation(  
         request = CreateOrUpdateConversationRequest(  
            conversationid = "api-comment-demo1",  
            property = "sportstalk247.com/apidemo",  
            moderation = "post",  
            maxreports = 0,  
            title = "Sample Conversation",  
            maxcommentlen = 512,  
            open = true,  
            customid = "/articles/2020-03-01/article1/something-very-important-happened",  
            customfield1 = "/sample/userdefined1/emojis/😂🤣❤😍😒",  
            customfield2 = "/sample/userdefined2/intl/characters/äöüÄÖÜß",  
            customtags = listOf("taga", "tagb"),  
            custompayload = "{ num : 0 }"  
         )  
      )
   }
     
   // Resolve `createConversationResponse` from HERE(ex. Display Prompt, update UI)  
}  
```  

```
### Bounce User (ban user from room)
```kotlin
// Under Fragment class
// Execute within coroutine scope
lifecycleScope.launch {
  // This is with the assumption that
   // 1. A test user has been created(or already exists)
   // 2. A test chat room has already been created(or already exists)
   // 3. Test user already joined the room
   val banUserInputRequest = BounceUserRequest(
      userid = testUserData.userid!!,
      bounce = true,
      announcement = "Test user has been banned."
   )
   
   val banUserResponse = withContext(Dispatchers.IO) {
      chatClient.bounceUser(
      chatRoomId = testChatRoomData.id!!,
      request = banUserInputRequest
    )
   }
    
   // Resolve `banUserResponse` from HERE(ex. Display Prompt, update UI)
   // Next call to getEventUpdates() now includes a ChatEvent with eventtype="bounce"
}  
```

### Create/Publish a comment against a Conversation
```kotlin  
// Under Fragment class  
// Execute within coroutine scope  
lifecycleScope.launch {  
   // Assuming that there is already a created conversation  
   val testConversation = Conversation(conversationid = "1234567890",...)  
     
   val publishCommentResponse = withContext(Dispatchers.IO) {  
      commentClient.publishComment(  
         conversationid = testConversation.conversationid!!,  
         request = PublishCommentRequest(  
            userid = "<USER-ID>",  
            body = "This is a comment",  
            added = "2020-05-02T08:51:53.8140055Z"  
         )  
      )  
   }
   
   // Resolve `publishCommentResponse` from HERE(ex. Display Prompt, update UI)
}  
```  
### Reply to a comment
```kotlin  
// Under Fragment class  
// Execute within coroutine scope  
lifecycleScope.launch {
   // Assuming that there is already a created conversation  
   val testConversation = Conversation(conversationid = "1234567890",...)  
   // Assuming that there is already atleast 1 comment under the conversation  
   val testComment = Comment(id = "0987654321",...)  
     
   val replyToCommentResponse = withContext(Dispatchers.IO) {  
      commentClient.replyToComment(  
         conversationid = testConversation.conversationid!!,  
         replyto = testComment.id!!,  
         request = PublishCommentRequest(  
            userid = "<USER-ID>",  
            body = "This is a reply...",  
            added = "2020-05-21T07:14:19.2424561Z"  
         )  
      ) 
   }  
     
   // Resolve `replyToCommentResponse` from HERE(ex. Display Prompt, update UI)  
}
```
### Flag comment logically deleted / Permanently Delete Comment
```kotlin  
// Under Fragment class  
// Execute within coroutine scope  
lifecycleScope.launch {  
   // Assuming that there is already a created conversation  
   val testConversation = Conversation(conversationid = "1234567890",...)  
   // Assuming that there is already atleast 1 comment under the conversation  
   val testComment = Comment(id = "0987654321",...)  
     
   val setCommentDeletedResponse = withContext(Dispatchers.IO) {  
      commentClient.permanentlyDeleteComment(  
         conversationid = testConversation.conversationid!!,  
         commentid = testComment.id!!  
      )
   }  
     
   // Resolve `setCommentDeletedResponse` from HERE(ex. Display Prompt, update UI)
}  
```
  
## Handling SDK Exception
If any client operations receive an error response, whether it be Network, Server, or Validation Error, these functions will throw an instance of `SportsTalkException`.  
```kotlin
data class SportsTalkException(
   val kind: String? = null, // "api.result"  
   val message: String? = null, // ex. "The specified comment was not found."  
   val code: Int? = null // ex. 404,
   val data: Map<String, String?>? = null,
   val err: Throwable? = null
)
  
// Under Fragment class
// Execute within coroutine scope
lifecycleScope.launch {
   val testComment = Comment(id = "0987654321",...)
   
   val setCommentDeletedResponse = try {  
      withContext(Dispatchers.IO) {  
         // These should throw Error 404 - "The specified conversation was not found and was not deleted.".  
         commentClient.permanentlyDeleteComment(  
            conversationid = "Non-existent-Conversation-ID",  
            commentid = testComment.id!!  
         )
      }
   } catch(err: SportsTalkException) {
      // Resolve ERROR from HERE.
      return
   }
}
```

## Work-in-progress...
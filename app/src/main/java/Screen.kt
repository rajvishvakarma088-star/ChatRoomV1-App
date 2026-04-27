import java.net.URLEncoder

sealed class Screen(val route:String){
    object LoginScreen:Screen("loginscreen")
    object SignupScreen:Screen("signupscreen")
    object ChatRoomsScreen:Screen("chatroomscreen")
    object ChatScreen: Screen("chatscreen") {
        fun withArgs(roomId: String, roomName: String): String {
            return "chatscreen/$roomId/${URLEncoder.encode(roomName, "UTF-8")}"
        }
    }
    object CallScreen: Screen("callscreen") {
        fun withArgs(roomId: String, roomName: String, callType: String, autoStart: Boolean): String {
            return "callscreen/$roomId/${URLEncoder.encode(roomName, "UTF-8")}/$callType/$autoStart"
        }
    }
}

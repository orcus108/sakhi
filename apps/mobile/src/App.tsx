import React, { useEffect } from "react";
import { SafeAreaView, StatusBar } from "react-native";
import { HomeScreen } from "./screens/HomeScreen";
import { migrateDb } from "./db/migrate";

export default function App() {
  useEffect(() => {
    migrateDb();
  }, []);

  return (
    <SafeAreaView style={{ flex: 1 }}>
      <StatusBar barStyle="dark-content" />
      <HomeScreen />
    </SafeAreaView>
  );
}

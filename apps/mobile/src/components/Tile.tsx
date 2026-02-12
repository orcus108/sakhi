import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

type Props = {
  title: string;
  subtitle: string;
  icon: string;
  onPress: () => void;
};

export function Tile({ title, subtitle, icon, onPress }: Props) {
  return (
    <Pressable style={styles.tile} onPress={onPress}>
      <View style={styles.iconWrap}>
        <Text style={styles.icon}>{icon}</Text>
      </View>
      <View style={styles.textWrap}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tile: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff9f1",
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: "#f4d9b8"
  },
  iconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#fde3c1"
  },
  icon: {
    fontSize: 20
  },
  textWrap: {
    marginLeft: 12,
    flexShrink: 1
  },
  title: {
    fontWeight: "700",
    fontSize: 16,
    color: "#2c2c2c"
  },
  subtitle: {
    marginTop: 2,
    color: "#5b5b5b"
  }
});

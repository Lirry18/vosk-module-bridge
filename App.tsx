/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * Generated with the TypeScript template
 * https://github.com/react-native-community/react-native-template-typescript
 *
 * @format
 */

import React, {Component} from 'react';
import {
  Button,
  NativeEventEmitter,
  NativeModules,
  StyleSheet,
  View,
} from 'react-native';


import VoskInterface from './VOSK'
const  Kaldi  = NativeModules.Kaldi



//declare const global: {HermesInternal: null | {}};



   
// Fix this later, to listen to the error

// eventEmitter.addListener("onSpeechReady", (event) => {
//   console.log(event) // "someValue"
// });
// eventEmitter.addListener("onSpeechResults", (event) => {
//   console.log(event) // "someValue"
// });

export default class App extends Component {
  constructor() {
    super();
    this.state = {
      message: '',
      userName : '',
      authToken: '',
      data: [
      ],
    };
  }


  componentDidMount() {
    const eventEmitter = new NativeEventEmitter(NativeModules.Kaldi);
      const eventListener = eventEmitter.addListener("onSpeechReady", (event) => {
        console.log(event) // "someValue"
      });

      Kaldi.initialize();
  }

  componentWillUnmount(){
    Kaldi.destroy();
  }

  render() {
    return (
      <View>
        <Button title="Hello world" onPress={() => Kaldi.startListening()}></Button>
      </View>
    );
  }
}




const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
  message: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
    marginTop: 15,
  },
});

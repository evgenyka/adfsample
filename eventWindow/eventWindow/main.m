//
//  main.m
//  eventWindow
//
//  Created by Evgeny Karasik on 11/02/2019.
//  Copyright © 2019 Evgeny Karasik. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "AppDelegate.h"
#import "Window.h"

int main(int argc, char * argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, NSStringFromClass([Window class]),
                                 NSStringFromClass([AppDelegate class]));
    }
}
